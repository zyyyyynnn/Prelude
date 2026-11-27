package main

import (
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

const maxUploadBytes = 10 << 20

func main() {
	backend, err := url.Parse("http://backend:8080")
	if err != nil {
		log.Fatal(err)
	}
	log.Fatal(http.ListenAndServe(":80", newHandler("/srv", backend)))
}

func newHandler(root string, backend *url.URL) http.Handler {
	proxy := httputil.NewSingleHostReverseProxy(backend)
	proxy.FlushInterval = -1
	files := http.FileServer(http.Dir(root))
	mux := http.NewServeMux()
	mux.HandleFunc("/api/", func(writer http.ResponseWriter, request *http.Request) {
		request.Body = http.MaxBytesReader(writer, request.Body, maxUploadBytes)
		for _, header := range []string{
			"Forwarded",
			"X-Forwarded-For",
			"X-Forwarded-Host",
			"X-Forwarded-Port",
			"X-Forwarded-Prefix",
		} {
			request.Header.Del(header)
		}
		proxy.ServeHTTP(writer, request)
	})
	mux.Handle("/", http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		relative := strings.TrimPrefix(filepath.Clean(request.URL.Path), "/")
		if info, err := os.Stat(filepath.Join(root, relative)); err == nil && !info.IsDir() {
			files.ServeHTTP(writer, request)
			return
		}
		http.ServeFile(writer, request, filepath.Join(root, "index.html"))
	}))
	return mux
}
