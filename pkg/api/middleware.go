package api

import (
	"net"
	"net/http"
	"sync"
	"time"
)

func (s *Server) cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		h.Set("Access-Control-Allow-Methods", "GET, OPTIONS")
		h.Set("Access-Control-Allow-Headers", "Accept, Content-Type")
		h.Set("Access-Control-Max-Age", "86400")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) rateLimit(next http.Handler) http.Handler {
	if s.limiter == nil {
		return next
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip := clientIP(r)
		if !s.limiter.allow(ip, time.Now()) {
			w.Header().Set("Retry-After", "1")
			writeError(w, r, http.StatusTooManyRequests, ErrRateLimited.Error())
			return
		}
		next.ServeHTTP(w, r)
	})
}

func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// limiter est un seau à jetons par IP, sans dépendance externe. La
// seconde de l'horloge système y sert uniquement de cadence de remplissage ;
// elle n'est jamais exposée.
type limiter struct {
	rate  float64 // jetons par seconde
	burst float64

	mu      sync.Mutex
	buckets map[string]*bucket
	ops     int
}

type bucket struct {
	tokens float64
	last   time.Time
}

func newLimiter(rate float64, burst int) *limiter {
	if burst < 1 {
		burst = 1
	}
	return &limiter{rate: rate, burst: float64(burst), buckets: make(map[string]*bucket)}
}

func (l *limiter) allow(key string, now time.Time) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	b, ok := l.buckets[key]
	if !ok {
		b = &bucket{tokens: l.burst, last: now}
		l.buckets[key] = b
	} else {
		b.tokens += now.Sub(b.last).Seconds() * l.rate
		if b.tokens > l.burst {
			b.tokens = l.burst
		}
		b.last = now
	}
	l.ops++
	if l.ops%1024 == 0 {
		l.prune(now)
	}
	if b.tokens < 1 {
		return false
	}
	b.tokens--
	return true
}

// prune supprime les seaux inactifs depuis assez longtemps pour être
// pleins de toute façon.
func (l *limiter) prune(now time.Time) {
	idle := time.Duration(l.burst/l.rate*float64(time.Second)) + time.Minute
	for k, b := range l.buckets {
		if now.Sub(b.last) > idle {
			delete(l.buckets, k)
		}
	}
}
