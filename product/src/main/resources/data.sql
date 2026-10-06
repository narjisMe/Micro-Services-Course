INSERT INTO PRODUCT (id, name, price, stock, version)
SELECT 1, 'Keyboard', 49.90, 10, 0
WHERE NOT EXISTS (SELECT 1 FROM PRODUCT WHERE id = 1);

INSERT INTO PRODUCT (id, name, price, stock, version)
SELECT 2, 'Mouse', 19.90, 50, 0
WHERE NOT EXISTS (SELECT 1 FROM PRODUCT WHERE id = 2);
