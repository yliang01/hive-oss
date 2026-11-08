package cc.cc3c.hive.oss.business.release;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

public class SelfReleaseControllerTest {
    @Test
    public void manifestResponseReturnsZipPackage() throws Exception {
        var service = mock(SelfReleaseService.class);
        byte[] packageBytes = new byte[]{1, 2, 3};
        when(service.manifestPackage("v1", 900)).thenReturn(packageBytes);
        var mvc = MockMvcBuilders.standaloneSetup(new SelfReleaseController(service)).build();
        var response = mvc.perform(post("/self-release/manifest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"seconds\":900}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=release-manifest.zip"))
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse();
        assertEquals(response.getContentAsByteArray(), packageBytes);
    }
}
