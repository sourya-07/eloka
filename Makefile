# One entry point for common tasks. Run `make help`.
.DEFAULT_GOAL := help
.PHONY: help setup dev dev-mock mock client-test client-lint client-build server-run server-test server-build load-test images ci-client test

help: ## List the available commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

setup: ## Install client dependencies and git hooks
	cd client && npm install

dev-mock: ## Website on :3002 with dummy logins (mock EPS on :4010)
	cd client && npm run dev:mock

dev: ## Website on :3002 against the EPS in client/.env.local
	cd client && npm run dev

mock: ## Only the mock EPS server on :4010
	cd client && npm run mock

client-test: ## Client unit tests + mock contract tests
	cd client && npm run test:mock && npm test

client-lint: ## Client lint (errors only)
	cd client && npm run lint:error

client-build: ## Client production build
	cd client && npm run build

server-run: ## Backend on :8090 with local MySQL in Docker
	cd server && ./gradlew bootRun

server-test: ## Backend tests (Docker needed for the database tests)
	cd server && ./gradlew test

server-build: ## Backend jar in server/build/libs
	cd server && ./gradlew bootJar

load-test: ## k6 load test of the home-page calls against the local server (:8090)
	docker run --rm -i -e BASE_URL=http://host.docker.internal:8090 grafana/k6:1.3.0 run - < server/load/home.js

ci-client: ## Client CI job locally, like GitHub (Node 20, steps one at a time, light on RAM)
	cd client && npx -y -p node@20 npm run lint:error
	cd client && npx -y -p node@20 npm run test:mock
	cd client && npx -y -p node@20 npm test -- --ci --maxWorkers=1
	cd client && NODE_OPTIONS=--max-old-space-size=2048 npx -y -p node@20 npm run build

images: ## Build both production images (deploy: docs/deploy/RUNBOOK.md)
	docker build -t eloka-server:local server
	docker build -t eloka-client:local --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8090 client

test: client-test server-test ## Everything
