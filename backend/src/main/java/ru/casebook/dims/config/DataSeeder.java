package ru.casebook.dims.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;
import ru.casebook.dims.service.SecurityHash;

import java.time.Instant;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(UserRepository users, CaseRepository cases, EvidenceRepository evidence, EvidenceVersionRepository versions, TaskRepository tasks) {
        return args -> {
            if (users.count() > 0) {
                return;
            }

            UserAccount sherlock = users.save(new UserAccount("sherlock", SecurityHash.sha256("holmes"), Role.DETECTIVE, "Шерлок Холмс"));
            UserAccount watson = users.save(new UserAccount("watson", SecurityHash.sha256("watson"), Role.ASSISTANT, "Доктор Ватсон"));
            users.save(new UserAccount("lestrade", SecurityHash.sha256("lestrade"), Role.INSPECTOR, "Инспектор Лестрейд"));
            UserAccount agent = users.save(new UserAccount("agent", SecurityHash.sha256("agent"), Role.AGENT, "Полевой агент"));
            users.save(new UserAccount("lab", SecurityHash.sha256("lab"), Role.LAB_ANALYST, "Лаборатория"));
            users.save(new UserAccount("admin", SecurityHash.sha256("admin"), Role.ADMIN, "Администратор"));

            Instant openedAt = Instant.parse("1894-06-18T09:00:00Z");
            CaseFile caseFile = cases.save(new CaseFile(
                    "CASE-1894-001",
                    "Дело о кольце Джефферсона Хоупа",
                    openedAt,
                    Priority.HIGH,
                    "Расследование смерти в Лористон-гарденс с найденным кольцом и противоречивыми уликами.",
                    sherlock
            ));

            Evidence ring = evidence.save(new Evidence(
                    caseFile,
                    "EV-1894-001",
                    "Женское кольцо",
                    "материальная",
                    Priority.HIGH,
                    "Кольцо найдено на месте происшествия. Может указывать на личный мотив.",
                    openedAt,
                    51.5007,
                    -0.1246,
                    "Лористон-гарденс",
                    watson
            ));
            versions.save(new EvidenceVersion(ring, ring.getDescription(), watson, 1));

            tasks.save(new TaskItem(
                    caseFile,
                    "Проверить ломбарды рядом с Брикстон-роуд",
                    "Собрать сведения о похожих кольцах и возможных владельцах.",
                    agent,
                    sherlock,
                    Priority.MEDIUM,
                    Instant.parse("1894-06-25T18:00:00Z")
            ));
        };
    }
}
