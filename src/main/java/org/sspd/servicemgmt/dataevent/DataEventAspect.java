package org.sspd.servicemgmt.dataevent;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Broadcasts a lightweight DataEventDTO to /topic/data-events after any
 * mutating service method (CREATE / UPDATE / DELETE / ACTION).
 *
 * Android clients subscribe once and call load() on the relevant screen
 * whenever a matching entity event arrives.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class DataEventAspect {

    private final DataEventPublisher publisher;

    @Pointcut("execution(* org.sspd.servicemgmt..service..*(..)) " +
              "&& !within(org.sspd.servicemgmt.auditoptions..*) " +
              "&& !within(org.sspd.servicemgmt.jwt..*) " +
              "&& !within(org.sspd.servicemgmt.chatoptions..*) " +
              "&& !within(org.sspd.servicemgmt.bookingoptions.service.BookingAlertService) " +
              "&& !within(org.sspd.servicemgmt.dataevent..*)")
    public void mutatingServices() {}

    @AfterReturning(pointcut = "mutatingServices()", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        try {
            String action = inferAction(jp.getSignature().getName());
            if (action == null) return;

            // Only broadcast when there is an authenticated user (not a scheduled task)
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) return;

            String entity     = extractModule(jp.getTarget().getClass().getSimpleName());
            String resourceId = extractResourceId(result);

            publisher.broadcast(entity, action, resourceId);
        } catch (Exception ignored) {
            // Never let broadcast failure break business logic
        }
    }

    // ── Helpers (mirror AuditAspect) ─────────────────────────────────────────

    private String inferAction(String name) {
        String n = name.toLowerCase();
        if (n.startsWith("save")   || n.startsWith("create") || n.startsWith("add"))    return "CREATE";
        if (n.startsWith("update") || n.startsWith("modify") || n.startsWith("edit"))   return "UPDATE";
        if (n.startsWith("delete") || n.startsWith("void")   || n.startsWith("remove")
                                    || n.startsWith("cancel"))                           return "DELETE";
        if (n.startsWith("pay")    || n.startsWith("mark")   || n.startsWith("approve")
                || n.startsWith("complete") || n.startsWith("process")
                || n.startsWith("reverse")  || n.startsWith("settle"))                  return "ACTION";
        return null;
    }

    private String extractModule(String className) {
        String name = className;
        if (name.contains("$$"))       name = name.substring(0, name.indexOf("$$"));
        if (name.endsWith("Service"))  name = name.substring(0, name.length() - 7);
        return name.replaceAll("([A-Z])", " $1").trim();
    }

    private String extractResourceId(Object result) {
        if (result == null) return null;
        try {
            for (String m : new String[]{
                    "getSaleCode", "getPurchaseCode", "getJobNo",
                    "getReturnCode", "getCode", "getTransactionNo"}) {
                try {
                    Method met = result.getClass().getMethod(m);
                    Object val = met.invoke(result);
                    if (val != null) return val.toString();
                } catch (NoSuchMethodException ignored) {}
            }
            Method getId = result.getClass().getMethod("getId");
            Object id    = getId.invoke(result);
            if (id != null) return id.toString();
        } catch (Exception ignored) {}
        return null;
    }
}
