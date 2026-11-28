package main

import (
	"bufio"
	"crypto/sha1"
	"encoding/base64"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestWebSocketUpgradeTunnelsBidirectionalFrames(t *testing.T) {
	backend := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if !strings.EqualFold(request.Header.Get("Upgrade"), "websocket") {
			http.Error(writer, "missing websocket upgrade", http.StatusBadRequest)
			return
		}
		accept := webSocketAccept(request.Header.Get("Sec-WebSocket-Key"))
		hijacker, ok := writer.(http.Hijacker)
		if !ok {
			http.Error(writer, "hijacking unavailable", http.StatusInternalServerError)
			return
		}
		connection, buffered, err := hijacker.Hijack()
		if err != nil {
			t.Errorf("hijack backend connection: %v", err)
			return
		}
		defer connection.Close()
		_, _ = fmt.Fprintf(buffered, "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\n\r\n", accept)
		if err := buffered.Flush(); err != nil {
			t.Errorf("flush backend handshake: %v", err)
			return
		}
		payload, err := readWebSocketFrame(buffered.Reader, true)
		if err != nil {
			t.Errorf("read backend frame: %v", err)
			return
		}
		if err := writeWebSocketFrame(buffered.Writer, payload, false); err != nil {
			t.Errorf("write backend frame: %v", err)
			return
		}
		if err := buffered.Writer.Flush(); err != nil {
			t.Errorf("flush backend frame: %v", err)
		}
	}))
	defer backend.Close()
	backendURL, _ := url.Parse(backend.URL)
	frontend := httptest.NewServer(newHandler(t.TempDir(), backendURL))
	defer frontend.Close()

	connection, err := net.Dial("tcp", frontend.Listener.Addr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	key := "dGhlIHNhbXBsZSBub25jZQ=="
	_, _ = fmt.Fprintf(connection, "GET /api/ws HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n", frontend.Listener.Addr().String(), key)
	reader := bufio.NewReader(connection)
	response, err := http.ReadResponse(reader, &http.Request{Method: http.MethodGet})
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusSwitchingProtocols || response.Header.Get("Sec-WebSocket-Accept") != webSocketAccept(key) {
		t.Fatalf("unexpected websocket handshake: %d %v", response.StatusCode, response.Header)
	}
	if err := writeWebSocketFrame(connection, []byte("voice-frame"), true); err != nil {
		t.Fatal(err)
	}
	payload, err := readWebSocketFrame(reader, false)
	if err != nil {
		t.Fatal(err)
	}
	if string(payload) != "voice-frame" {
		t.Fatalf("unexpected websocket payload: %q", payload)
	}
}

func webSocketAccept(key string) string {
	digest := sha1.Sum([]byte(key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))
	return base64.StdEncoding.EncodeToString(digest[:])
}

func writeWebSocketFrame(writer io.Writer, payload []byte, masked bool) error {
	frame := []byte{0x81, byte(len(payload))}
	if masked {
		frame[1] |= 0x80
		mask := []byte{0x01, 0x02, 0x03, 0x04}
		frame = append(frame, mask...)
		for index, value := range payload {
			frame = append(frame, value^mask[index%len(mask)])
		}
	} else {
		frame = append(frame, payload...)
	}
	_, err := writer.Write(frame)
	return err
}

func readWebSocketFrame(reader io.Reader, masked bool) ([]byte, error) {
	header := make([]byte, 2)
	if _, err := io.ReadFull(reader, header); err != nil {
		return nil, err
	}
	length := uint64(header[1] & 0x7f)
	if length == 126 {
		extended := make([]byte, 2)
		if _, err := io.ReadFull(reader, extended); err != nil {
			return nil, err
		}
		length = uint64(binary.BigEndian.Uint16(extended))
	}
	var mask [4]byte
	if masked {
		if _, err := io.ReadFull(reader, mask[:]); err != nil {
			return nil, err
		}
	}
	payload := make([]byte, length)
	if _, err := io.ReadFull(reader, payload); err != nil {
		return nil, err
	}
	if masked {
		for index := range payload {
			payload[index] ^= mask[index%len(mask)]
		}
	}
	return payload, nil
}

func TestStaticFilesAndSpaFallback(t *testing.T) {
	root := t.TempDir()
	if err := os.WriteFile(filepath.Join(root, "index.html"), []byte("app shell"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.Mkdir(filepath.Join(root, "assets"), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "assets", "app.js"), []byte("asset"), 0o600); err != nil {
		t.Fatal(err)
	}
	backend, _ := url.Parse("http://backend:8080")
	handler := newHandler(root, backend)

	for path, expected := range map[string]string{"/interview": "app shell", "/assets/app.js": "asset"} {
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, path, nil))
		if recorder.Code != http.StatusOK || recorder.Body.String() != expected {
			t.Fatalf("%s returned %d %q", path, recorder.Code, recorder.Body.String())
		}
	}
}

func TestApiProxyPreservesPathAndBodyAndTrustsOnlyClientAddress(t *testing.T) {
	backendServer := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		if request.Header.Get("Forwarded") != "" || request.Header.Get("X-Forwarded-Host") != "example.com" ||
			request.Header.Get("X-Forwarded-Port") != "14173" || request.Header.Get("X-Forwarded-Prefix") != "" ||
			request.Header.Get("X-Forwarded-For") != "192.0.2.1" ||
			request.Header.Get("X-Forwarded-Proto") != "http" {
			writer.WriteHeader(http.StatusBadRequest)
			_, _ = writer.Write([]byte("untrusted proxy headers"))
			return
		}
		writer.WriteHeader(http.StatusAccepted)
		_, _ = writer.Write([]byte(request.URL.Path + ":" + string(body)))
	}))
	defer backendServer.Close()
	backend, _ := url.Parse(backendServer.URL)
	handler := newHandler(t.TempDir(), backend)
	request := httptest.NewRequest(http.MethodPost, "/api/interview/7/chat", strings.NewReader("answer"))
	request.Host = "example.com:14173"
	for _, header := range []string{
		"Forwarded",
		"X-Forwarded-For",
		"X-Forwarded-Host",
		"X-Forwarded-Port",
		"X-Forwarded-Prefix",
		"X-Forwarded-Proto",
	} {
		request.Header.Set(header, "spoofed")
	}
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	if recorder.Code != http.StatusAccepted || recorder.Body.String() != "/api/interview/7/chat:answer" {
		t.Fatalf("proxy returned %d %q", recorder.Code, recorder.Body.String())
	}
}
