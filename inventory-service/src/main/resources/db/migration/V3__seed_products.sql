-- Reference catalogue. Stock arrives through POST /api/v1/lots, not through a
-- migration, so the receiving flow is exercised rather than bypassed.
INSERT INTO product (sku, name, category, description, unit, shelf_life_days, allergens) VALUES
  ('CHK-BRST-5LB',  'Chicken Breast Boneless 5lb',  'POULTRY',  'Fresh boneless skinless chicken breast, food service pack', 'CASE', 7,  '{}'),
  ('CHK-THGH-5LB',  'Chicken Thigh Boneless 5lb',   'POULTRY',  'Fresh boneless chicken thigh, food service pack',           'CASE', 7,  '{}'),
  ('CHK-TNDR-4LB',  'Chicken Tenderloin 4lb',       'POULTRY',  'Fresh chicken tenderloin strips, food service pack',        'CASE', 6,  '{}'),
  ('TKY-BRST-5LB',  'Turkey Breast Boneless 5lb',   'POULTRY',  'Fresh boneless turkey breast, food service pack',           'CASE', 8,  '{}'),
  ('BEF-GRND-10LB', 'Ground Beef 80/20 10lb',       'BEEF',     'Fresh ground beef eighty twenty chuck blend',               'CASE', 5,  '{}'),
  ('BEF-SRLN-8LB',  'Beef Sirloin Steak 8lb',       'BEEF',     'Fresh beef sirloin steak portion cut',                      'CASE', 6,  '{}'),
  ('SLM-FLLT-4LB',  'Atlantic Salmon Fillet 4lb',   'SEAFOOD',  'Fresh Atlantic salmon fillet skin on',                      'CASE', 4,  '{fish}'),
  ('SHR-1621-5LB',  'Shrimp 16/21 Peeled 5lb',      'SEAFOOD',  'Frozen peeled deveined shrimp sixteen twenty one count',    'CASE', 90, '{shellfish}'),
  ('MLK-WHL-4GAL',  'Whole Milk 4 Gallon',          'DAIRY',    'Pasteurised whole milk gallon jugs',                        'CASE', 12, '{milk}'),
  ('CHZ-MOZZ-5LB',  'Mozzarella Shredded 5lb',      'DAIRY',    'Low moisture part skim shredded mozzarella cheese',         'CASE', 30, '{milk}'),
  ('CHZ-CHDR-5LB',  'Cheddar Shredded 5lb',         'DAIRY',    'Sharp yellow shredded cheddar cheese',                      'CASE', 30, '{milk}'),
  ('LET-ROM-24CT',  'Romaine Lettuce 24ct',         'PRODUCE',  'Fresh romaine lettuce hearts twenty four count',            'CASE', 9,  '{}'),
  ('LET-ICE-24CT',  'Iceberg Lettuce 24ct',         'PRODUCE',  'Fresh iceberg lettuce heads twenty four count',             'CASE', 11, '{}'),
  ('TOM-ROMA-25LB', 'Roma Tomatoes 25lb',           'PRODUCE',  'Fresh roma plum tomatoes bulk case',                        'CASE', 10, '{}'),
  ('BRD-BRGR-96CT', 'Burger Buns 96ct',             'BAKERY',   'Sliced sesame burger buns ninety six count',                'CASE', 5,  '{wheat,sesame,soy}'),
  ('BRD-SUB-72CT',  'Sub Rolls 72ct',               'BAKERY',   'Sliced sub sandwich rolls seventy two count',               'CASE', 5,  '{wheat,soy}');
