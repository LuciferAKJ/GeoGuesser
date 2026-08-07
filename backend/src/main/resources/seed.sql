-- Safe initial seed file with expanded global locations and balanced difficulties.
-- Uses ON CONFLICT to ensure idempotent execution.

INSERT INTO locations (latitude, longitude, country, city, difficulty) VALUES
  -- EASY (Iconic Landmarks & Major Global Capitals)
  (48.8584, 2.2945, 'France', 'Paris (Eiffel Tower)', 'EASY'),
  (51.5074, -0.1278, 'United Kingdom', 'London (Big Ben)', 'EASY'),
  (40.7128, -74.0060, 'USA', 'New York (Statue of Liberty)', 'EASY'),
  (35.6762, 139.6503, 'Japan', 'Tokyo (Tokyo Tower)', 'EASY'),
  (41.9028, 12.4964, 'Italy', 'Rome (Colosseum)', 'EASY'),
  (27.1751, 78.0421, 'India', 'Agra (Taj Mahal)', 'EASY'),
  (-33.8568, 151.2153, 'Australia', 'Sydney (Opera House)', 'EASY'),
  (29.9792, 31.3428, 'Egypt', 'Giza (Pyramids)', 'EASY'),
  (37.9715, 23.7269, 'Greece', 'Athens (Parthenon)', 'EASY'),
  (41.8902, 12.4922, 'Vatican City', 'Vatican (St. Peters Basilica)', 'EASY'),

  -- MEDIUM (Recognizable Regional Hubs & Scenery)
  (-22.9068, -43.1729, 'Brazil', 'Rio de Janeiro (Christ the Redeemer)', 'MEDIUM'),
  (55.7558, 37.6176, 'Russia', 'Moscow (Red Square)', 'MEDIUM'),
  (30.0444, 31.2357, 'Egypt', 'Cairo (Downtown)', 'MEDIUM'),
  (19.0760, 72.8777, 'India', 'Mumbai (Gateway of India)', 'MEDIUM'),
  (1.3521, 103.8198, 'Singapore', 'Singapore (Marina Bay)', 'MEDIUM'),
  (34.0522, -118.2437, 'USA', 'Los Angeles (Hollywood Sign)', 'MEDIUM'),
  (43.6532, -79.3832, 'Canada', 'Toronto (CN Tower)', 'MEDIUM'),
  (19.4326, -99.1332, 'Mexico', 'Mexico City (Zocalo)', 'MEDIUM'),
  (52.5200, 13.4050, 'Germany', 'Berlin (Brandenburg Gate)', 'MEDIUM'),
  (45.4215, -75.6972, 'Canada', 'Ottawa (Parliament Hill)', 'MEDIUM'),

  -- HARD (Remote, Subtle Terrain, or Challenging Island Capitals)
  (-1.2921, 36.8219, 'Kenya', 'Nairobi', 'HARD'),
  (60.1699, 24.9384, 'Finland', 'Helsinki', 'HARD'),
  (-34.6037, -58.3816, 'Argentina', 'Buenos Aires', 'HARD'),
  (33.8869, 9.5375, 'Tunisia', 'Tunis', 'HARD'),
  (47.4979, 19.0402, 'Hungary', 'Budapest', 'HARD'),
  (59.9139, 10.7522, 'Norway', 'Oslo', 'HARD'),
  (14.6928, -17.4467, 'Senegal', 'Dakar', 'HARD'),
  (31.7683, 35.2137, 'Israel', 'Jerusalem', 'HARD'),
  (-17.7333, 168.3220, 'Vanuatu', 'Port Vila', 'HARD'),
  (64.1466, -21.9426, 'Iceland', 'Reykjavik', 'HARD')
ON CONFLICT DO NOTHING;