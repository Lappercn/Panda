package org.AI.panda.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String purposeLabel, String code, int ttlMinutes) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("邮件账号未配置: spring.mail.username 为空");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Panda 知识库 - 邮箱验证码");
        message.setText("""
                你好，

                你正在进行：%s
                你的验证码是：%s
                有效期：%d 分钟

                如果不是你本人操作，请忽略本邮件。
                """.formatted(purposeLabel, code, ttlMinutes));

        mailSender.send(message);
    }
}
