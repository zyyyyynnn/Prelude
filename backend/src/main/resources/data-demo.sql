INSERT INTO `user` (`username`, `password`, `email`)
SELECT 'demo',
       '$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2',
       'demo@example.com'
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE `username` = 'demo'
);
