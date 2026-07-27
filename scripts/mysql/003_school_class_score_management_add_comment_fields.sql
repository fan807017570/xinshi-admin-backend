-- Add richer comment fields for course results and overall comments

ALTER TABLE school_student_course_result
    ADD COLUMN  strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Course strengths',
    ADD COLUMN  improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Course improvement points';

ALTER TABLE school_student_overall_comment
    ADD COLUMN strengths VARCHAR(1000) DEFAULT NULL COMMENT 'Overall strengths' ,
    ADD COLUMN  improvement_points VARCHAR(1000) DEFAULT NULL COMMENT 'Overall improvement points' ;
