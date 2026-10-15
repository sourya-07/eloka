# One entry point for common tasks. Run `make help`.
.DEFAULT_GOAL := help
.PHONY: help setup dev dev-mock mock client-test client-lint client-build server-run server-test server-build test

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

test: client-test server-test ## Everything
