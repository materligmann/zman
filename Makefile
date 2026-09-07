.PHONY: build run test vet examples og web docker deploy clean

build:
	go build -o bin/server ./cmd/server

run:
	go run ./cmd/server

test:
	go vet ./...
	go test ./...

vet:
	go vet ./...

## Régénère les réponses réelles affichées sur la page /api (lance un serveur local).
examples:
	scripts/gen-examples.sh

## Régénère l'image Open Graph (PNG) à partir des polices embarquées.
og:
	python3 scripts/gen-og.py

## Le site n'a pas d'étape de build : cette cible vérifie seulement que tout est embarqué.
web: examples og
	go build ./...

docker:
	docker build -t zman .

deploy:
	./deploy.sh

clean:
	rm -rf bin
