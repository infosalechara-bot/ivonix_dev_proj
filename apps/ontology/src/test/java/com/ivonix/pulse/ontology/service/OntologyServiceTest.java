package com.ivonix.pulse.ontology.service;

import com.ivonix.pulse.ontology.repository.OntologyEntityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OntologyServiceTest {
  @Test void graphClampsDepthToEight(){
    JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);
    OntologyService service=new OntologyService(Mockito.mock(OntologyEntityRepository.class),jdbc);
    var id=java.util.UUID.randomUUID(); var org=java.util.UUID.randomUUID();
    service.graph(id,999,org);
    Mockito.verify(jdbc).queryForList("select * from public.get_entity_graph(?, ?, ?)",id,8,org);
  }
  @Test void graphClampsNegativeDepthToZero(){
    JdbcTemplate jdbc=Mockito.mock(JdbcTemplate.class);
    OntologyService service=new OntologyService(Mockito.mock(OntologyEntityRepository.class),jdbc);
    var id=java.util.UUID.randomUUID(); var org=java.util.UUID.randomUUID();
    service.graph(id,-5,org);
    Mockito.verify(jdbc).queryForList("select * from public.get_entity_graph(?, ?, ?)",id,0,org);
    assertEquals(0,Math.max(0,Math.min(-5,8)));
  }
}