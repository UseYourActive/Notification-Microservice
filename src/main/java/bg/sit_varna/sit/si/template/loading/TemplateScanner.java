package bg.sit_varna.sit.si.template.loading;

import bg.sit_varna.sit.si.constant.NotificationErrorCode;
import bg.sit_varna.sit.si.exception.exceptions.TemplateScanException;
import bg.sit_varna.sit.si.service.core.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class TemplateScanner {

    private static final Logger LOG = Logger.getLogger(TemplateScanner.class);
    private static final String TEMPLATES_PATH = "templates";

    private final MessageService messageService;

    @Inject
    public TemplateScanner(MessageService messageService) {
        this.messageService = messageService;
    }

    public List<String> scanTemplateFiles() {
        URI uri = getResourceURI();
        Path templatesPath = getTemplatesPath(uri);

        try (Stream<Path> paths = Files.walk(templatesPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> normalizeFilePath(templatesPath.relativize(path).toString()))
                    .toList();
        } catch (IOException e) {
            throw new TemplateScanException(
                    NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                    messageService.getTitle(NotificationErrorCode.TEMPLATE_SCAN_ERROR),
                    messageService.getMessage(NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                            "Failed to walk templates directory: " + e.getMessage()),
                    e
            );
        }
    }

    private URI getResourceURI() {
        try {
            return getClass().getClassLoader().getResource(TEMPLATES_PATH).toURI();
        } catch (URISyntaxException e) {
            throw new TemplateScanException(
                    NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                    messageService.getTitle(NotificationErrorCode.TEMPLATE_SCAN_ERROR),
                    messageService.getMessage(NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                            "Failed to get templates URI: " + e.getMessage()),
                    e
            );
        }
    }

    private Path getTemplatesPath(URI uri) {
        try {
            if (uri.getScheme().equals("jar")) {
                FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                return fileSystem.getPath(TEMPLATES_PATH);
            } else {
                return Paths.get(uri);
            }
        } catch (IOException e) {
            throw new TemplateScanException(
                    NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                    messageService.getTitle(NotificationErrorCode.TEMPLATE_SCAN_ERROR),
                    messageService.getMessage(NotificationErrorCode.TEMPLATE_SCAN_ERROR,
                            "Failed to initialize filesystem for templates: " + e.getMessage()),
                    e
            );
        }
    }

    private String normalizeFilePath(String filePath) {
        return filePath.replace('\\', '/');
    }
}
