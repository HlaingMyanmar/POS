package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;

import java.util.Properties;

@Slf4j
@Service
public class CustomerMailService {

    /**
     * Same origin as Android {@code BuildConfig.DEFAULT_BASE_URL}
     * ({@code http://118.27.151.89}) — never use domain for reset links.
     */
    private static final String DEFAULT_BASE_URL = "http://118.27.151.89";

    private final ObjectProvider<JavaMailSender> envMailSender;
    private final CompanySettingsService companySettingsService;
    private final String envFrom;

    public CustomerMailService(
            ObjectProvider<JavaMailSender> envMailSender,
            CompanySettingsService companySettingsService,
            @Value("${app.customer-portal.mail-from:}") String envFrom
    ) {
        this.envMailSender = envMailSender;
        this.companySettingsService = companySettingsService;
        this.envFrom = envFrom;
    }

    public String resetUrl(String token) {
        return DEFAULT_BASE_URL + "/customer/reset-password?token=" + token;
    }

    public void sendPasswordReset(String to, String token) {
        String url = resetUrl(token);
        String body = "SSPD Customer\n\n"
                + "Password ပြန်သတ်မှတ်ရန် ဤလင့်ခ်ကို ဖွင့်ပါ (၅ မိနစ် သက်တမ်း):\n"
                + url + "\n\n"
                + "အရေးကြီး — လင့်ခ်ဖွင့်ပြီး Reset email (" + to + ") နဲ့ တူညီသော Google အကောင့်ဖြင့် ဝင်မှ password ပြောင်းနိုင်ပါသည်။\n"
                + "ပြီးရင် ဖုန်းထဲက SSPD Customer APK မှ စကားဝှက်အသစ်ဖြင့် ဝင်ပါ။\n\n"
                + "သင်မတောင်းထားပါက ဤစာကို လျစ်လျူရှုပါ။";
        send(to, "SSPD — Password ပြန်သတ်မှတ်ရန်", body);
    }

    public void sendTest(String to) {
        send(to, "SSPD — Test email",
                "SSPD Company Settings SMTP စမ်းသပ်မှု အောင်မြင်ပါသည်။\n\nThis is a test message from your POS mail settings.");
    }

    public void sendPdf(String to, String subject, String body, String filename, byte[] pdf) {
        ResolvedMail mail = resolveSender();
        if (mail == null) {
            throw new IllegalStateException("Email မပို့နိုင်ပါ။ Company Settings တွင် Gmail SMTP ထည့်ပါ");
        }
        if (!StringUtils.hasText(to) || !to.contains("@")) {
            throw new IllegalArgumentException("ပို့မည့် email မှန်ကန်စွာ ထည့်ပါ");
        }
        try {
            var message = mail.sender().createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            if (StringUtils.hasText(mail.from())) helper.setFrom(mail.from());
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(body == null ? "" : body);
            helper.addAttachment(
                    filename == null ? "invoice.pdf" : filename,
                    new ByteArrayResource(pdf == null ? new byte[0] : pdf),
                    "application/pdf"
            );
            mail.sender().send(message);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PDF email send failed: {}", e.getMessage());
            throw new IllegalStateException("Email မပို့နိုင်ပါ။ SMTP / App Password စစ်ဆေးပါ");
        }
    }

    private void send(String to, String subject, String body) {
        ResolvedMail mail = resolveSender();
        if (mail == null) {
            log.warn("SMTP မရှိပါ။ Company Settings သို့မဟုတ် MAIL_HOST သတ်မှတ်ပါ");
            throw new IllegalStateException("Email မပို့နိုင်ပါ။ Company Settings တွင် Gmail SMTP ထည့်ပါ");
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            if (StringUtils.hasText(mail.from())) msg.setFrom(mail.from());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mail.sender().send(msg);
        } catch (Exception e) {
            log.warn("Email send failed: {}", e.getMessage());
            throw new IllegalStateException("Email မပို့နိုင်ပါ။ SMTP / App Password စစ်ဆေးပါ");
        }
    }

    private ResolvedMail resolveSender() {
        CompanySettingsService.MailSmtpConfig cfg = companySettingsService.resolveMailSmtp();
        if (cfg.isConfigured()) {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(cfg.host());
            sender.setPort(cfg.port());
            sender.setUsername(cfg.username());
            sender.setPassword(cfg.password());
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", String.valueOf(cfg.auth()));
            props.put("mail.smtp.starttls.enable", String.valueOf(cfg.startTls()));
            props.put("mail.smtp.starttls.required", String.valueOf(cfg.startTls()));
            return new ResolvedMail(sender, cfg.from());
        }

        JavaMailSender env = envMailSender.getIfAvailable();
        if (env == null) return null;
        String from = StringUtils.hasText(envFrom) ? envFrom : null;
        return new ResolvedMail(env, from);
    }

    private record ResolvedMail(JavaMailSender sender, String from) {}
}
