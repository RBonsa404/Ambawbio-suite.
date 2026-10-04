package bf.ambawbio.sync.operations;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/** Accepte les envois de synchronisation compressés (Content-Encoding: gzip), indispensables en 3G (ENF-02). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class FiltreDecompression extends OncePerRequestFilter {

    private static final long TAILLE_MAX = 10L * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !"gzip".equalsIgnoreCase(requete.getHeader("Content-Encoding")) || !requete.getRequestURI().startsWith("/api/v1/sync/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain chaine) throws ServletException, IOException {
        var flux = new GZIPInputStream(requete.getInputStream());
        chaine.doFilter(new HttpServletRequestWrapper(requete) {
            private long lus;

            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override
                    public int read() throws IOException {
                        if (++lus > TAILLE_MAX) {
                            throw new IOException("Envoi trop volumineux");
                        }
                        return flux.read();
                    }

                    @Override
                    public int read(byte[] b, int off, int len) throws IOException {
                        int n = flux.read(b, off, len);
                        lus += Math.max(n, 0);
                        if (lus > TAILLE_MAX) {
                            throw new IOException("Envoi trop volumineux");
                        }
                        return n;
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener ecouteur) {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public String getHeader(String nom) {
                return "Content-Encoding".equalsIgnoreCase(nom) ? null : super.getHeader(nom);
            }

            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        }, reponse);
    }
}
