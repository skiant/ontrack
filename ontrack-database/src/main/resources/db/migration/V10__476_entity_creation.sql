-- Index on promotion & validation names (#472)

ALTER TABLE PROJECTS ADD CREATION VARCHAR(24);
ALTER TABLE PROJECTS ADD CREATOR VARCHAR(40);

ALTER TABLE BRANCHES ADD CREATION VARCHAR(24);
ALTER TABLE BRANCHES ADD CREATOR VARCHAR(40);

ALTER TABLE PROMOTION_LEVELS ADD CREATION VARCHAR(24);
ALTER TABLE PROMOTION_LEVELS ADD CREATOR VARCHAR(40);

ALTER TABLE VALIDATION_STAMPS ADD CREATION VARCHAR(24);
ALTER TABLE VALIDATION_STAMPS ADD CREATOR VARCHAR(40);
