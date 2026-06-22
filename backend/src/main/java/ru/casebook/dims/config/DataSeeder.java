package ru.casebook.dims.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;
import ru.casebook.dims.service.SecurityHash;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(UserRepository users, CaseRepository cases, EvidenceRepository evidence,
            EvidenceVersionRepository versions, TaskRepository tasks, CaseParticipantRepository participants,
            IncidentSceneRepository scenes, InterviewRepository interviews, LabRequestRepository labs,
            HypothesisRepository hypotheses, GraphEdgeRepository edges, ReportRepository reports,
            DimsProperties properties) {
        return args -> {
            UserAccount detective=user(users,"sherlock","holmes",Role.DETECTIVE,"Шерлок Холмс");
            UserAccount assistant=user(users,"watson","watson",Role.ASSISTANT,"Доктор Ватсон");
            UserAccount inspector=user(users,"lestrade","lestrade",Role.INSPECTOR,"Инспектор Лестрейд");
            UserAccount agent=user(users,"agent","agent",Role.AGENT,"Полевой агент");
            UserAccount lab=user(users,"lab","lab",Role.LAB_ANALYST,"Лаборатория");
            user(users,"admin","admin",Role.ADMIN,"Администратор");

            if(cases.findByRegistrationNumber("CASE-2026-DEMO-01").isEmpty()) seedCourier(cases,evidence,versions,tasks,participants,scenes,interviews,labs,hypotheses,edges,detective,assistant,agent,lab);
            if(cases.findByRegistrationNumber("CASE-2026-DEMO-02").isEmpty()) seedMuseum(cases,evidence,versions,tasks,participants,scenes,interviews,labs,hypotheses,edges,detective,assistant,inspector,agent,lab);
            if(cases.findByRegistrationNumber("CASE-2026-DEMO-03").isEmpty()) seedCyber(cases,evidence,versions,tasks,participants,scenes,interviews,hypotheses,edges,reports,properties,detective,assistant,agent);
        };
    }

    private UserAccount user(UserRepository repo,String login,String password,Role role,String name){return repo.findByLogin(login).orElseGet(()->repo.save(new UserAccount(login,SecurityHash.sha256(password),role,name)));}
    private void access(CaseParticipantRepository repo,CaseFile item,UserAccount... users){for(UserAccount user:users)repo.save(new CaseParticipant(item,user));}
    private Evidence evidence(EvidenceRepository repo,EvidenceVersionRepository versions,CaseFile item,String number,String name,String type,Priority priority,String description,String location,UserAccount author){Evidence value=repo.save(new Evidence(item,number,name,type,priority,description,Instant.now().minusSeconds(86_400),55.75,37.61,location,author));versions.save(new EvidenceVersion(value,description,author,1));return value;}
    private GraphEdge edge(GraphEdgeRepository edges,HypothesisRepository hypotheses,CaseFile item,NodeType fromType,java.util.UUID from,NodeType toType,java.util.UUID to,String semantic,String title,String text,Confidence confidence,UserAccount author){Hypothesis hypothesis=hypotheses.save(new Hypothesis(item,title,text,confidence,author));return edges.save(new GraphEdge(item,fromType,from,toType,to,semantic,confidence,hypothesis,author));}

    private void seedCourier(CaseRepository cases,EvidenceRepository evidence,EvidenceVersionRepository versions,TaskRepository tasks,CaseParticipantRepository participants,IncidentSceneRepository scenes,InterviewRepository interviews,LabRequestRepository labs,HypothesisRepository hypotheses,GraphEdgeRepository edges,UserAccount detective,UserAccount assistant,UserAccount agent,UserAccount lab){
        CaseFile item=cases.save(new CaseFile("CASE-2026-DEMO-01","Исчезновение курьера «Северный маршрут»",Instant.now().minusSeconds(172_800),Priority.HIGH,"Активное полевое расследование: пропавший курьер, брошенный фургон, геоданные телефона и несколько проверяемых версий.",detective));
        item.update(item.getTitle(),item.getOpenedAt(),item.getPriority(),CaseStatus.IN_PROGRESS,item.getDescription()); access(participants,item,detective,assistant,agent);
        IncidentScene warehouse=scenes.save(new IncidentScene(item,"Заброшенный склад №7","Фургон найден с открытой задней дверью; следов борьбы нет.","Промышленный проезд, 7",55.7601,37.6187,agent));
        Evidence phone=evidence(evidence,versions,item,"EV-DEMO-01-001","Телефон курьера","цифровая",Priority.CRITICAL,"Телефон найден под пассажирским сиденьем. Последняя геометка ведет к складу.","Фургон курьера",assistant);
        Evidence packageItem=evidence(evidence,versions,item,"EV-DEMO-01-002","Поврежденная упаковка","материальная",Priority.HIGH,"На упаковке обнаружен неизвестный волокнистый след и частичный отпечаток.","Склад №7",agent);
        interviews.save(new Interview(item,"Ночной охранник склада",Instant.now().minusSeconds(75_000),"Около 23:40 видел темный автомобиль без номерных знаков и двух человек у ворот.",detective));
        TaskItem task=tasks.save(new TaskItem(item,"TASK-DEMO-01-001","Восстановить маршрут курьера","Сопоставить GPS, камеры и данные базовых станций.",agent,detective,Priority.HIGH,Instant.now().plusSeconds(172_800))); task.updateStatus(TaskStatus.IN_PROGRESS,"Маршрут восстанавливается");
        labs.save(new LabRequest(item,"LAB-DEMO-01-001",packageItem,"Дактилоскопия и волокна","Выделить отпечаток и определить происхождение волокна.",Instant.now().plusSeconds(259_200),detective,lab));
        edge(edges,hypotheses,item,NodeType.LOCATION,warehouse.getId(),NodeType.EVIDENCE,phone.getId(),"объясняет местоположение","Курьера привезли на склад","Геометка телефона совпадает с местом обнаружения фургона.",Confidence.HIGH,detective);
        edge(edges,hypotheses,item,NodeType.EVIDENCE,packageItem.getId(),NodeType.TASK,task.getId(),"требует проверки","След ведет к автомобилю","Волокно может происходить из салона автомобиля похитителей.",Confidence.MEDIUM,detective);
    }

    private void seedMuseum(CaseRepository cases,EvidenceRepository evidence,EvidenceVersionRepository versions,TaskRepository tasks,CaseParticipantRepository participants,IncidentSceneRepository scenes,InterviewRepository interviews,LabRequestRepository labs,HypothesisRepository hypotheses,GraphEdgeRepository edges,UserAccount detective,UserAccount assistant,UserAccount inspector,UserAccount agent,UserAccount lab){
        CaseFile item=cases.save(new CaseFile("CASE-2026-DEMO-02","Подмена экспоната в городском музее",Instant.now().minusSeconds(604_800),Priority.CRITICAL,"Расследование подмены редкой рукописи копией. Демонстрирует завершенную экспертизу, выполненные поручения и противоречивые версии.",detective)); item.update(item.getTitle(),item.getOpenedAt(),item.getPriority(),CaseStatus.IN_PROGRESS,item.getDescription()); access(participants,item,detective,assistant,inspector,agent);
        IncidentScene archive=scenes.save(new IncidentScene(item,"Закрытый архив музея","Пломба цела, электронный замок фиксировал сервисный вход.","Музейная площадь, 1",55.7522,37.6156,inspector));
        Evidence fiber=evidence(evidence,versions,item,"EV-DEMO-02-001","Синее синтетическое волокно","материальная",Priority.HIGH,"Волокно снято с внутренней стороны защитной витрины.","Архив музея",assistant); fiber.setStatus(EvidenceStatus.EXAMINATION_COMPLETED);
        Evidence accessLog=evidence(evidence,versions,item,"EV-DEMO-02-002","Журнал электронного замка","цифровая",Priority.CRITICAL,"Зафиксирован сервисный ключ сотрудника реставрационной мастерской.","Сервер службы безопасности",inspector);
        interviews.save(new Interview(item,"Главный хранитель",Instant.now().minusSeconds(400_000),"Утверждает, что сервисный ключ находился в сейфе, однако журнал выдачи не заполнен.",detective));
        TaskItem task=tasks.save(new TaskItem(item,"TASK-DEMO-02-001","Проверить сервисные ключи","Сверить журнал доступа, записи камер и показания сотрудников.",agent,detective,Priority.CRITICAL,Instant.now().plusSeconds(86_400))); task.updateStatus(TaskStatus.DONE,"Подтвержден вход сотрудника мастерской в 02:14; камера коридора была отключена.");
        LabRequest request=labs.save(new LabRequest(item,"LAB-DEMO-02-001",fiber,"Материаловедческая экспертиза","Сравнить волокно со спецодеждой сотрудников.",Instant.now().plusSeconds(86_400),detective,lab)); request.complete("Волокно соответствует ткани защитного халата реставрационной мастерской. ".repeat(170));
        edge(edges,hypotheses,item,NodeType.EVIDENCE,accessLog.getId(),NodeType.LOCATION,archive.getId(),"подтверждает доступ","Использован штатный сервисный ключ","Запись замка подтверждает физический вход в архив.",Confidence.CONFIRMED,detective);
        edge(edges,hypotheses,item,NodeType.EVIDENCE,fiber.getId(),NodeType.TASK,task.getId(),"подтверждает версию","Причастность мастерской","Лабораторный результат связывает витрину со спецодеждой мастерской.",Confidence.HIGH,detective);
    }

    private void seedCyber(CaseRepository cases,EvidenceRepository evidence,EvidenceVersionRepository versions,TaskRepository tasks,CaseParticipantRepository participants,IncidentSceneRepository scenes,InterviewRepository interviews,HypothesisRepository hypotheses,GraphEdgeRepository edges,ReportRepository reports,DimsProperties properties,UserAccount detective,UserAccount assistant,UserAccount agent) throws Exception {
        CaseFile item=cases.save(new CaseFile("CASE-2026-DEMO-03","Серия фишинговых атак на городской портал",Instant.now().minusSeconds(1_209_600),Priority.MEDIUM,"Завершенное цифровое расследование с цепочкой электронных доказательств, выполненной задачей и утвержденным отчетом.",detective)); item.update(item.getTitle(),item.getOpenedAt(),item.getPriority(),CaseStatus.CLOSED,item.getDescription()); access(participants,item,detective,assistant,agent);
        IncidentScene office=scenes.save(new IncidentScene(item,"Рабочее место администратора","Изъят ноутбук, с которого была открыта фишинговая ссылка.","Центр цифровых услуг",55.7489,37.6208,agent));
        Evidence logs=evidence(evidence,versions,item,"EV-DEMO-03-001","Экспорт почтовых журналов","цифровая",Priority.CRITICAL,"Заголовки писем, DKIM-данные и IP-адреса отправителей сохранены с контрольной суммой.","Почтовый шлюз",assistant);
        Evidence laptop=evidence(evidence,versions,item,"EV-DEMO-03-002","Образ диска ноутбука","цифровая",Priority.HIGH,"Образ содержит кэш браузера и скрипт перенаправления на поддельный домен.","Рабочее место администратора",agent);
        interviews.save(new Interview(item,"Администратор портала",Instant.now().minusSeconds(1_000_000),"Подтвердил получение письма о срочном обновлении сертификата и переход по ссылке.",detective));
        TaskItem task=tasks.save(new TaskItem(item,"TASK-DEMO-03-001","Атрибутировать инфраструктуру","Сопоставить домены, сертификаты и адреса управляющих серверов.",agent,detective,Priority.HIGH,Instant.now().minusSeconds(172_800))); task.updateStatus(TaskStatus.DONE,"Инфраструктура связана с одной группой по повторно использованному сертификату и адресу оплаты домена.");
        edge(edges,hypotheses,item,NodeType.EVIDENCE,logs.getId(),NodeType.EVIDENCE,laptop.getId(),"коррелирует","Единая фишинговая кампания","URL из почтового журнала совпадает с адресом в кэше браузера.",Confidence.CONFIRMED,detective);
        edge(edges,hypotheses,item,NodeType.LOCATION,office.getId(),NodeType.EVIDENCE,laptop.getId(),"место изъятия","Сохранена цепочка хранения","Образ создан непосредственно после изъятия устройства.",Confidence.HIGH,detective);
        String content="ИТОГОВЫЙ ОТЧЕТ\nДело: "+item.getRegistrationNumber()+"\nВывод: источник фишинговой кампании установлен, цифровая цепочка доказательств подтверждена.";
        Path directory=Path.of(properties.getStoragePath(),"reports",item.getId().toString()).toAbsolutePath().normalize(); Files.createDirectories(directory); Path file=directory.resolve("REP-DEMO-03-001.txt"); Files.writeString(file,content,StandardCharsets.UTF_8);
        ReportFile report=reports.save(new ReportFile(item,"REP-DEMO-03-001","TEXT",ReportStatus.APPROVED,file.toString(),SecurityHash.sha256(content.getBytes(StandardCharsets.UTF_8)),detective,content));
        edges.save(new GraphEdge(item,NodeType.CASE,item.getId(),NodeType.REPORT,report.getId(),"завершено отчетом",Confidence.CONFIRMED,null,detective));
    }
}
