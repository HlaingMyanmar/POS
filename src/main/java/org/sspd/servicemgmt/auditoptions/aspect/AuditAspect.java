package org.sspd.servicemgmt.auditoptions.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Pointcut("execution(* org.sspd.servicemgmt..service..*(..)) " +
              "&& !within(org.sspd.servicemgmt.auditoptions..*) " +
              "&& !within(org.sspd.servicemgmt.jwt..*) " +
              "&& !within(org.sspd.servicemgmt.chatoptions..*)")
    public void auditableServices() {}

    @AfterReturning(pointcut = "auditableServices()", returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        try {
            String methodName = jp.getSignature().getName();
            String action = inferAction(methodName);
            if (action == null) return;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) return;

            String actor     = auth.getName();
            String actorRole = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .findFirst()
                    .map(r -> r.replace("ROLE_", ""))
                    .orElse("");

            String className = jp.getTarget().getClass().getSimpleName();
            String module    = extractModule(className);
            String resId     = extractResourceId(result);
            String ip        = getClientIp();
            String device    = getDeviceType();
            String desc      = action + " " + module + (resId != null ? " [" + resId + "]" : "");

            auditLogService.log(actor, actorRole, action, module, resId, desc, ip, device);
        } catch (Exception ignored) {
            // Never let audit failure break business logic
        }
    }

    private String inferAction(String name) {
        String n = name.toLowerCase();
        if (n.startsWith("save") || n.startsWith("create") || n.startsWith("add")) return "CREATE";
        if (n.startsWith("update") || n.startsWith("modify") || n.startsWith("edit")) return "UPDATE";
        if (n.startsWith("delete") || n.startsWith("void") || n.startsWith("remove") || n.startsWith("cancel")) return "DELETE";
        if (n.startsWith("pay") || n.startsWith("mark") || n.startsWith("approve") ||
            n.startsWith("complete") || n.startsWith("process") || n.startsWith("reverse")) return "ACTION";
        return null;
    }

    private String extractModule(String className) {
        String name = className;
        if (name.contains("$$")) name = name.substring(0, name.indexOf("$$"));
        if (name.endsWith("Service")) name = name.substring(0, name.length() - 7);
        return name.replaceAll("([A-Z])", " $1").trim();
    }

    private String extractResourceId(Object result) {
        if (result == null) return null;
        try {
            for (String m : new String[]{"getSaleCode","getPurchaseCode","getJobNo","getReturnCode","getCode","getTransactionNo"}) {
                try {
                    Method met = result.getClass().getMethod(m);
                    Object val = met.invoke(result);
                    if (val != null) return val.toString();
                } catch (NoSuchMethodException ignored) {}
            }
            Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            if (id != null) return "#" + id;
        } catch (Exception ignored) {}
        return null;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "N/A";
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            return (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : req.getRemoteAddr();
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String getDeviceType() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "UNKNOWN";
            String ua = attrs.getRequest().getHeader("User-Agent");
            if (ua == null) return "UNKNOWN";
            if (ua.contains("okhttp") || ua.contains("Expo") || ua.contains("ReactNative")) return "MOBILE";
            return "WEB";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
