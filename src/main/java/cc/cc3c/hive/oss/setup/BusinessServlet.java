package cc.cc3c.hive.oss.setup;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;

/** Registered at server startup; delegates only after the business context is ready. */
public final class BusinessServlet extends HttpServlet {
    private volatile DispatcherServlet dispatcher;

    public synchronized void activate(WebApplicationContext context) throws ServletException {
        var candidate = new DispatcherServlet(context);
        candidate.setPublishContext(false);
        try {
            candidate.init(getServletConfig());
            dispatcher = candidate;
        } catch (ServletException | RuntimeException exception) {
            candidate.destroy();
            throw exception;
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var current = dispatcher;
        if (current == null) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"BUSINESS_NOT_READY\"}");
            return;
        }
        current.service(request, response);
    }

    public synchronized void deactivate() {
        var current = dispatcher;
        dispatcher = null;
        if (current != null) current.destroy();
    }

    @Override
    public void destroy() { deactivate(); }
}
