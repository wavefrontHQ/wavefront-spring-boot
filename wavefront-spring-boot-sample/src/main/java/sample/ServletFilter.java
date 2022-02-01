package sample;

import org.springframework.cloud.sleuth.Span;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.cloud.sleuth.Tracer;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

@Component
public class ServletFilter extends GenericFilterBean {

    private final Tracer tracer;

    public ServletFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Span currentSpan = this.tracer.currentSpan();

        if (currentSpan == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            chain.doFilter(request, response);
        }
        catch (Exception e) {
            currentSpan.event(String.valueOf(e));
            throw e;
        }
    }
}