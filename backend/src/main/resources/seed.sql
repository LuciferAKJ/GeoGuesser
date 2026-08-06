-- Run this once to seed your database with starter locations.
-- Mapbox image URLs are generated automatically by LocationService
-- if left blank, so image_url can be NULL here.

INSERT INTO locations (latitude, longitude, country, city, difficulty) VALUES
  (48.8584,   2.2945,   'France',        'Paris',         'EASY'),
  (51.5074,  -0.1278,   'United Kingdom','London',         'EASY'),
  (40.7128,  -74.0060,  'USA',           'New York',       'EASY'),
  (35.6762,  139.6503,  'Japan',         'Tokyo',          'EASY'),
  (41.9028,   12.4964,  'Italy',         'Rome',           'EASY'),
  (-33.8688,  151.2093, 'Australia',     'Sydney',         'MEDIUM'),
  (55.7558,   37.6176,  'Russia',        'Moscow',         'MEDIUM'),
  (-22.9068, -43.1729,  'Brazil',        'Rio de Janeiro', 'MEDIUM'),
  (30.0444,   31.2357,  'Egypt',         'Cairo',          'MEDIUM'),
  (19.0760,   72.8777,  'India',         'Mumbai',         'MEDIUM'),
  (1.3521,   103.8198,  'Singapore',     'Singapore',      'MEDIUM'),
  (-1.2921,   36.8219,  'Kenya',         'Nairobi',        'HARD'),
  (60.1699,   24.9384,  'Finland',       'Helsinki',       'HARD'),
  (-34.6037, -58.3816,  'Argentina',     'Buenos Aires',   'HARD'),
  (33.8869,    9.5375,  'Tunisia',       'Tunis',          'HARD'),
  (47.4979,   19.0402,  'Hungary',       'Budapest',       'HARD'),
  (59.9139,   10.7522,  'Norway',        'Oslo',           'HARD'),
  (14.6928,  -17.4467,  'Senegal',       'Dakar',          'HARD'),
  (31.7683,   35.2137,  'Israel',        'Jerusalem',      'HARD'),
  (-17.7333, 168.3220,  'Vanuatu',       'Port Vila',      'HARD');
