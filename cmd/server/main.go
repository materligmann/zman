// Command server lance le serveur de temps juif : le site public et l'API
// sous /api/, dans un seul binaire.
//
// Configuration par variables d'environnement :
//
//	PORT               port d'écoute (défaut 8080)
//	BASE_URL           URL publique, ex. https://zman.example (défaut : déduite de la requête)
//	REPO_URL           URL du dépôt de code, affichée sur le site
//	IERS_CACHE_PATH    chemin du cache disque finals.all (défaut ./cache/finals.all)
//	IERS_CACHE_GCS     cache partagé GCS "bucket/objet" (optionnel)
//	IERS_URL           URL(s) de finals.all, séparées par des virgules
//	IERS_REFRESH_HOURS intervalle de rafraîchissement (défaut 168 = hebdomadaire)
//	RATE_LIMIT_RPS     requêtes par unité de cadence et par IP sur l'API (défaut 20, 0 = désactivé)
//	RATE_LIMIT_BURST   rafale autorisée (défaut 60)
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"zman/pkg/api"
	"zman/pkg/clock"
	"zman/pkg/iers"
	"zman/pkg/web"
	webfs "zman/web"
)

func main() {
	log.SetFlags(log.LstdFlags | log.Lmsgprefix)
	log.SetPrefix("zman: ")

	port := env("PORT", "8080")
	cachePath := env("IERS_CACHE_PATH", "./cache/finals.all")
	refresh := time.Duration(envFloat("IERS_REFRESH_HOURS", 168)) * time.Hour
	rps := envFloat("RATE_LIMIT_RPS", 20)
	burst := int(envFloat("RATE_LIMIT_BURST", 60))

	opts := []iers.Option{iers.WithRefresh(refresh), iers.WithLogger(log.Printf)}
	if u := os.Getenv("IERS_URL"); u != "" {
		var urls []string
		for _, s := range strings.Split(u, ",") {
			if s = strings.TrimSpace(s); s != "" {
				urls = append(urls, s)
			}
		}
		opts = append(opts, iers.WithURLs(urls...))
	}
	if spec := os.Getenv("IERS_CACHE_GCS"); spec != "" {
		g, err := iers.NewGCS(spec)
		if err != nil {
			log.Fatal(err)
		}
		opts = append(opts, iers.WithRemote(g))
	}
	client := iers.NewClient(cachePath, opts...)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	loadCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	if err := client.Load(loadCtx); err != nil {
		log.Printf("IERS data unavailable, running in fallback mode (UT1 ≈ UTC): %v", err)
	}
	cancel()
	client.Start(ctx)

	apiSrv := api.New(clock.NewUT1(client),
		api.WithPrefix("/api"),
		api.WithStatus(func() api.DataStatus {
			st := client.Status()
			return api.DataStatus{
				Loaded: st.Loaded, Records: st.Records, MeasuredRecords: st.MeasuredRecords,
				Source: st.Source, CacheAgeDays: round1(st.CacheAgeDays), LastError: st.LastError,
			}
		}),
		api.WithRateLimit(rps, burst),
	)
	site, err := web.New(webfs.FS, apiSrv.Handler(), web.Config{
		BaseURL: os.Getenv("BASE_URL"),
		Repo:    env("REPO_URL", "https://github.com/materligmann/zman"),
		Version: os.Getenv("K_REVISION"), // Cloud Run : une révision = une version d'assets
	})
	if err != nil {
		log.Fatalf("site: %v", err)
	}

	hs := &http.Server{
		Addr:              ":" + port,
		Handler:           web.SecurityHeaders(site),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	go func() {
		log.Printf("listening on :%s", port)
		if err := hs.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server: %v", err)
		}
	}()

	<-ctx.Done()
	log.Print("shutting down")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	_ = hs.Shutdown(shutdownCtx)
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envFloat(key string, def float64) float64 {
	if v := os.Getenv(key); v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
		log.Printf("ignoring invalid %s=%q", key, v)
	}
	return def
}

func round1(f float64) float64 {
	return float64(int64(f*10+0.5)) / 10
}
