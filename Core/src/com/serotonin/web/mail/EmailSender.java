package com.serotonin.web.mail;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;

public class EmailSender {
    private final JavaMailSenderImpl senderImpl;

    //
    // /
    // / Constructors
    // /
    //
    public EmailSender(EmailConfiguration config) {
        this(config.getHost(), config.getPort(), config.isUseAuth(), config.getUsername(), config.getPassword(), config
                .isTls());
    }

    public EmailSender(String host, boolean useAuth, String userName, String password) {
        this(host, 25, useAuth, userName, password);
    }

    public EmailSender(String host, int port, boolean useAuth, String userName, String password) {
        this(host, port, useAuth, userName, password, false);
    }

    public EmailSender(String host, int port, boolean useAuth, String userName, String password, boolean tls) {
        senderImpl = new JavaMailSenderImpl();
        Properties props = new Properties();
        if (useAuth) {
            props.put("mail.smtp.auth", "true");
            senderImpl.setUsername(userName);
            senderImpl.setPassword(password);
        }
        if (tls)
            props.put("mail.smtp.starttls.enable", "true");
        senderImpl.setJavaMailProperties(props);
        senderImpl.setHost(host);
        if (port != -1)
            senderImpl.setPort(port);
    }

    public EmailSender(String host) {
        this(host, false, null, null);
    }

    public EmailSender(String host, String userName, String password) {
        this(host, true, userName, password);
    }

    //
    // /
    // / Senders
    // /
    //
    public void send(String fromAddr, String toAddr, String subject, String contentPlain, String contentHtml) {
        send(fromAddr, null, toAddr, subject, new EmailContent(contentPlain, contentHtml));
    }

    public void send(String fromAddr, String toAddr, String subject, EmailContent content) {
        send(fromAddr, null, toAddr, subject, content);
    }

    public void send(String fromAddr, String fromPersonal, String toAddr, String subject, String contentPlain,
            String contentHtml) {
        send(fromAddr, fromPersonal, toAddr, subject, new EmailContent(contentPlain, contentHtml));
    }

    public void send(InternetAddress from, String toAddr, String subject, EmailContent content) {
        try {
            send(from, new InternetAddress[] { new InternetAddress(toAddr) }, null, null, subject, content);
        }
        catch (AddressException e) {
            throw new MailPreparationException(e);
        }
    }

    public void send(String fromAddr, String fromPersonal, String toAddr, String subject, EmailContent content) {
        try {
            send(new InternetAddress(fromAddr, fromPersonal), new InternetAddress[] { new InternetAddress(toAddr) },
                    null, null, subject, content);
        }
        catch (AddressException e) {
            throw new MailPreparationException(e);
        }
        catch (UnsupportedEncodingException e) {
            throw new MailPreparationException(e);
        }
    }

    public void send(String fromAddr, String[] toAddr, String subject, String contentPlain, String contentHtml) {
        send(fromAddr, null, toAddr, subject, new EmailContent(contentPlain, contentHtml));
    }

    public void send(String fromAddr, String[] toAddr, String subject, EmailContent content) {
        send(fromAddr, null, toAddr, subject, content);
    }

    public void send(String fromAddr, String fromPersonal, String[] toAddr, String subject, String contentPlain,
            String contentHtml) {
        send(fromAddr, fromPersonal, toAddr, subject, new EmailContent(contentPlain, contentHtml));
    }

    public void send(InternetAddress from, String toAddr[], String subject, EmailContent content) {
        try {
            InternetAddress[] toIAddr = new InternetAddress[toAddr.length];
            for (int i = 0; i < toAddr.length; i++)
                toIAddr[i] = new InternetAddress(toAddr[i]);
            send(from, toIAddr, null, null, subject, content);
        }
        catch (AddressException e) {
            throw new MailPreparationException(e);
        }
    }

    public void send(String fromAddr, String fromPersonal, String[] toAddr, String subject, EmailContent content) {
        try {
            InternetAddress[] toIAddr = new InternetAddress[toAddr.length];
            for (int i = 0; i < toAddr.length; i++)
                toIAddr[i] = new InternetAddress(toAddr[i]);
            send(new InternetAddress(fromAddr, fromPersonal), toIAddr, null, null, subject, content);
        }
        catch (AddressException e) {
            throw new MailPreparationException(e);
        }
        catch (UnsupportedEncodingException e) {
            throw new MailPreparationException(e);
        }
    }

    public void send(final InternetAddress from, final InternetAddress[] to, final String subject,
            final EmailContent content) {
        send(from, to, null, null, subject, content);
    }

    public MimeMessagePreparator createPreparator(final InternetAddress from, final InternetAddress replyTo,
            final InternetAddress[] to, final InternetAddress[] cc, final InternetAddress[] bcc, final String subject,
            final EmailContent content) throws MailException {
        return new MimeMessagePreparator() {
            public void prepare(MimeMessage mimeMessage) throws Exception {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, content.isMultipart(),
                        content.getEncoding());
                helper.setFrom(from);
                if (replyTo != null)
                    helper.setReplyTo(replyTo);
                helper.setTo(to);
                if (cc != null)
                    helper.setCc(cc);
                if (bcc != null)
                    helper.setBcc(bcc);

                // Ensure that line breaks in the subject are removed.
                String sub;
                if (subject == null)
                    sub = "";
                else {
                    sub = subject.replaceAll("\\r", "");
                    sub = sub.replaceAll("\\n", "");
                }

                helper.setSubject(sub);

                if (content.getHtmlContent() == null)
                    helper.setText(content.getPlainContent(), false);
                else if (content.getPlainContent() == null)
                    helper.setText(content.getHtmlContent(), true);
                else
                    helper.setText(content.getPlainContent(), content.getHtmlContent());

                for (EmailAttachment att : content.getAttachments())
                    att.attach(helper);
                for (EmailInline inline : content.getInlines())
                    inline.attach(helper);
            }
        };
    }

    public void send(InternetAddress from, InternetAddress[] to, InternetAddress[] cc, InternetAddress[] bcc,
            String subject, EmailContent content) throws MailException {
        send(createPreparator(from, null, to, cc, bcc, subject, content));
    }

    public void send(InternetAddress from, InternetAddress replyTo, InternetAddress[] to, InternetAddress[] cc,
            InternetAddress[] bcc, String subject, EmailContent content) throws MailException {
        send(createPreparator(from, replyTo, to, cc, bcc, subject, content));
    }

    public void send(MimeMessagePreparator mimeMessagePreparator) throws MailException {
        senderImpl.send(mimeMessagePreparator);
    }

    public void send(MimeMessagePreparator[] mimeMessagePreparators) throws MailException {
        senderImpl.send(mimeMessagePreparators);
    }
}
