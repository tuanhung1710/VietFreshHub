package VietFreshHub.Auth;

import VietFreshHub.Auth.service.CloudinaryStorageService;
import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeedDocumentUrlTest {
    @Test void seededDocumentUrlIsNotTreatedAsCloudinaryPublicId() {
        var cloudinary=mock(Cloudinary.class);
        var service=new CloudinaryStorageService(cloudinary);
        String url="https://example.invalid/seed/giay-phep-kinh-doanh.pdf";
        assertEquals(url,service.createPrivateDownloadUrl(url,"raw","pdf"));
        verifyNoInteractions(cloudinary);
    }
}
