package bg.sit_varna.sit.si.template.loading;

import bg.sit_varna.sit.si.service.core.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TemplateScannerTest {

    private final TemplateScanner scanner = new TemplateScanner(mock(MessageService.class));

    @Test
    void scanTemplateFiles_discoversAllRealTemplateFilesWithForwardSlashPaths() {
        List<String> files = scanner.scanTemplateFiles();

        assertThat(files).containsExactlyInAnyOrder(
                "email/password_reset_bg.html",
                "email/password_reset_en.html",
                "email/welcome_bg.html",
                "email/welcome_en.html",
                "sms/appointment_reminder_bg.txt",
                "sms/appointment_reminder_en.txt",
                "sms/verification_code_bg.txt",
                "sms/verification_code_en.txt",
                "telegram/daily_reminder_bg.txt",
                "telegram/daily_reminder_en.txt",
                "telegram/password_reset_bg.txt",
                "telegram/password_reset_en.txt",
                "telegram/welcome_bg.txt",
                "telegram/welcome_en.txt"
        );
        assertThat(files).allMatch(path -> !path.contains("\\"));
    }

    // The IO/URI-exception branches in getResourceURI()/getTemplatesPath() need an
    // unreachable "templates" classpath resource or a filesystem fault - neither
    // triggerable deterministically without an injectable resource root, which would be
    // a production change out of scope here. Left uncovered; the happy path above
    // already exercises the real production templates directory end-to-end.
}
