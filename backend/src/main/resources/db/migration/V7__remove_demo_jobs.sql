-- Demo postings are only development fixtures; production review should show imported sources.
delete from job_posting where source = 'DEMO';
