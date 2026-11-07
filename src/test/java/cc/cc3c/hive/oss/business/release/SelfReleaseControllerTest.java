package cc.cc3c.hive.oss.business.release;

import cc.cc3c.hive.release.common.DownloadTicket;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public void ticketResponseRetainsDownloadJsonFormat() throws Exception {
        var service = mock(SelfReleaseService.class);
        var ticket = new DownloadTicket("v1", "2030-01-01T00:00:00Z", "https://example.test/program",
                "https://example.test/config", "https://example.test/manifest");
        when(service.ticket("v1", 900)).thenReturn(ticket);
        var mvc = MockMvcBuilders.standaloneSetup(new SelfReleaseController(service)).build();
        var response = mvc.perform(post("/self-release/ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"v1\",\"seconds\":900}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=download.ticket.json"))
                .andReturn().getResponse();
        var json = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(json.path("configuration_url").asText(), ticket.configurationUrl());
        assertEquals(json.path("program_url").asText(), ticket.programUrl());
        assertEquals(json.path("manifest_url").asText(), ticket.manifestUrl());
        assertEquals(new ObjectMapper().treeToValue(json, DownloadTicket.class), ticket);
    }
}
