# syntax=docker/dockerfile:1
FROM golang:1.25-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

# Image finale : distroless statique, sans shell, utilisateur non root,
# certificats CA inclus (nécessaires pour télécharger finals.all en HTTPS).
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/server /server
ENV PORT=8080 IERS_CACHE_PATH=/tmp/finals.all
EXPOSE 8080
ENTRYPOINT ["/server"]
