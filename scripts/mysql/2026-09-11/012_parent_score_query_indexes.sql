CREATE INDEX idx_result_student_term_exam_status
    ON school_student_course_result (student_id, academic_term_id, exam_type_id, status);
