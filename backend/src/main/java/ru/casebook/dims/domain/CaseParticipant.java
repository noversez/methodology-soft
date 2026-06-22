package ru.casebook.dims.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="case_participants", uniqueConstraints=@UniqueConstraint(columnNames={"case_file_id","user_account_id"}))
public class CaseParticipant {
    @Id private UUID id=UUID.randomUUID();
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private CaseFile caseFile;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private UserAccount userAccount;
    @Column(nullable=false) private Instant addedAt=Instant.now();
    protected CaseParticipant() {}
    public CaseParticipant(CaseFile caseFile,UserAccount userAccount){this.caseFile=caseFile;this.userAccount=userAccount;}
    public UUID getId(){return id;} public CaseFile getCaseFile(){return caseFile;} public UserAccount getUserAccount(){return userAccount;} public Instant getAddedAt(){return addedAt;}
}
