/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.springframework.stereotype.Service
 */
package com.xinshi.admin.application.academicterm;

import com.xinshi.admin.domain.academicterm.AcademicTerm;
import com.xinshi.admin.domain.academicterm.AcademicTermRepository;
import com.xinshi.admin.domain.shared.AuthorizationService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AcademicTermApplicationService {
    private final AcademicTermRepository academicTermRepository;
    private final AuthorizationService authorizationService;

    public AcademicTermApplicationService(AcademicTermRepository academicTermRepository, AuthorizationService authorizationService) {
        this.academicTermRepository = academicTermRepository;
        this.authorizationService = authorizationService;
    }

    public List<AcademicTerm> listAcademicTerms() {
        return this.academicTermRepository.findAll();
    }

    public AcademicTerm getAcademicTerm(long id) {
        return this.academicTermRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
    }

    public AcademicTerm createAcademicTerm(String termCode, String academicYear, String termName, LocalDate startDate, LocalDate endDate, int status) {
        this.authorizationService.ensureSuperAdmin();
        if (this.academicTermRepository.findByCode(termCode).isPresent()) {
            throw new IllegalArgumentException("学期编码已存在");
        }
        AcademicTerm term = AcademicTerm.create(termCode, academicYear, termName, startDate, endDate, status);
        return this.academicTermRepository.save(term);
    }

    public AcademicTerm updateAcademicTerm(long id, String termName, LocalDate startDate, LocalDate endDate, Integer status) {
        this.authorizationService.ensureSuperAdmin();
        AcademicTerm term = this.getAcademicTerm(id);
        term.update(termName, startDate, endDate, status);
        this.academicTermRepository.update(term);
        return this.getAcademicTerm(id);
    }
}

