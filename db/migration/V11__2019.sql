	
-- Adds a column
ALTER TABLE user
    ADD COLUMN `rsview` text;

-- Adds a message
INSERT IGNORE INTO preference 
	VALUES ('message', ' ');
	

	