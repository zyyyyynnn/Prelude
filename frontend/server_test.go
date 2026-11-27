package main

import (
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

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

func TestApiProxyPreservesPathAndBody(t *testing.T) {
	backendServer := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		body, _ := io.ReadAll(request.Body)
		writer.WriteHeader(http.StatusAccepted)
		_, _ = writer.Write([]byte(request.URL.Path + ":" + string(body)))
	}))
	defer backendServer.Close()
	backend, _ := url.Parse(backendServer.URL)
	handler := newHandler(t.TempDir(), backend)
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodPost, "/api/interview/7/chat", strings.NewReader("answer")))

	if recorder.Code != http.StatusAccepted || recorder.Body.String() != "/api/interview/7/chat:answer" {
		t.Fatalf("proxy returned %d %q", recorder.Code, recorder.Body.String())
	}
}
