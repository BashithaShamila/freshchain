# All targets run inside Docker. Nothing needs to be installed locally except Docker.
.DEFAULT_GOAL := help
COMPOSE := docker compose

help: ## Show available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

build: ## Compile everything and run unit tests (no Docker-in-Docker needed)
	./scripts/gradle.sh build -x test
	./scripts/gradle.sh test --tests '*Test'

test: ## Full test suite, including Testcontainers integration tests
	./scripts/gradle.sh build

unit: ## Unit tests only (fast, no containers)
	./scripts/gradle.sh test --tests '*Test'

integration: ## Testcontainers integration + concurrency tests
	./scripts/gradle.sh test --tests '*IT'

concurrency: ## The headline oversell test
	./scripts/gradle.sh :inventory-service:test --tests '*ConcurrentReservationIT'

chaos: ## Kill the broker mid-run and prove the outbox catches up
	./scripts/gradle.sh :inventory-service:chaosTest

infra: ## Start Postgres, Kafka, Redis, Prometheus, Grafana (identity is Asgardeo, hosted)
	$(COMPOSE) up -d postgres kafka redis prometheus grafana

up: ## Build images and start the whole stack
	$(COMPOSE) --profile apps up -d --build

down: ## Stop everything, keep volumes
	$(COMPOSE) --profile apps down

clean: ## Stop everything and delete volumes
	$(COMPOSE) --profile apps down -v
	docker volume rm -f freshchain-gradle-cache

logs: ## Tail application logs
	$(COMPOSE) --profile apps logs -f gateway order-service inventory-service fulfillment-service

login: ## Sign the demo users in via Asgardeo (browser, once; then cached)
	python3 ./scripts/asgardeo-login.py --all

reconfigure: ## Recreate ALL services so they pick up .env changes (no rebuild needed)
	@# Compose does not restart a container when .env changes, and a partial
	@# recreate is worse than none: a gateway that accepts a token while the
	@# service behind it still rejects it fails as a confusing 401.
	$(COMPOSE) --profile apps up -d
	@sleep 3 && curl -s http://localhost:$${FRONTEND_PORT:-3001}/config.js

token: ## Copy a demo user's token to the clipboard: make token AS=customer|customer2|operator|admin
	@./scripts/copy-token.sh $(or $(AS),customer)

check-idp: ## Decode a cached token and show what Asgardeo actually put in it
	./scripts/asgardeo-check.sh

seed: ## Load demo products and stock
	./scripts/seed.sh

demo: ## Run the end-to-end happy-path + compensation demo
	./scripts/demo.sh

load: ## k6 load test against the gateway (raises the rate limit for the run)
	@test -d .tokens || { echo "run 'make login' first"; exit 1; }
	FRESHCHAIN_RATE_LIMIT_REPLENISH=2000 FRESHCHAIN_RATE_LIMIT_BURST=4000 $(COMPOSE) --profile apps up -d gateway
	docker run --rm --network freshchain -v "$$PWD/load":/load \
	  -e GATEWAY_URL=http://gateway:8080 \
	  -e FRESHCHAIN_TOKEN_1="$$(./scripts/token.sh customer)" \
	  -e FRESHCHAIN_TOKEN_2="$$(./scripts/token.sh customer2)" \
	  grafana/k6:latest run /load/order-placement.js

.PHONY: help build test unit integration concurrency chaos infra up down clean logs login reconfigure token check-idp seed demo load
