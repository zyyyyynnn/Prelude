group "default" {
  targets = ["backend", "frontend"]
}

target "backend" {
  context    = "."
  dockerfile = "backend/Dockerfile"
  tags       = ["prelude-backend:ci"]
  cache-from = ["type=gha,scope=prelude-backend"]
  cache-to   = ["type=gha,scope=prelude-backend,mode=max"]
}

target "frontend" {
  context    = "."
  dockerfile = "frontend/Dockerfile"
  tags       = ["prelude-frontend:ci"]
  cache-from = ["type=gha,scope=prelude-frontend"]
  cache-to   = ["type=gha,scope=prelude-frontend,mode=max"]
}
