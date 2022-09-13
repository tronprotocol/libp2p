package org.tron.p2p;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
@Slf4j
@EnableScheduling
// @RestController
public class P2pService {
  public static void main(String[] args) {
    SpringApplication.run(P2pService.class, args);
  }
}
