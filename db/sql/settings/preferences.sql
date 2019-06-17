-- Adds the preferences to the preferences table.
INSERT INTO preference(p_key, p_value) VALUES 
    ('document_directory', '/opt/ohmage/userdata/documents'), 
    ('image_directory', '/opt/ohmage/userdata/images'), 
    ('max_files_per_dir', '1000'), 
    ('document_depth', '5'), 
    ('visualization_server_address', 'http://opencpu.org/R/pub/Mobilize'),
    ('max_survey_response_page_size', '-1'),
    ('recaptcha_public_key', '6LfkfzIUAAAAAGUF_AU9dZI8Yqkyp3M5f5_e4ilJ'),
    ('recaptcha_private_key', '6LfkfzIUAAAAAOT0Np2UUdzgtoWbwGrfasY4NxRl'),
    ('public_class_id', 'urn:class:public'),
    ('video_directory', '/opt/ohmage/userdata/videos'),
    ('audio_directory', '/opt/ohmage/userdata/audios'),
    ('file_directory', '/opt/ohmage/userdata/files'),
    ('audit_log_location', '/opt/ohmage/logs/audits/'),
    ('fully_qualified_domain_name', 'localhost'),
    ('ssl_enabled', 'false');
