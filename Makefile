.PHONY: up down logs test smoke

up:
	docker compose up --build -d
	@echo ""
	@echo "  UI:        http://localhost:3000"
	@echo "  RabbitMQ:  http://localhost:15672  (guest / guest)"

down:
	docker compose down -v

logs:
	docker compose logs -f order-service payment-service inventory-service saga-orchestrator

test:
	./gradlew test

smoke:
	./scripts/smoke-test.sh
