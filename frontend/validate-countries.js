// Validation script for countries.json
const fs = require('fs');

try {
  const data = JSON.parse(fs.readFileSync('countries.json', 'utf8'));

  console.log('=== COUNTRIES.JSON VALIDATION ===\n');

  // 1. Total features
  const totalFeatures = data.features.length;
  console.log(`Total features: ${totalFeatures}`);

  // 2. Unique country names
  const countryNames = data.features.map(f => f.properties.name);
  const uniqueNames = new Set(countryNames);
  console.log(`Unique country names: ${uniqueNames.size}`);

  // 3. Check for duplicates
  const duplicates = countryNames.filter((name, index) => countryNames.indexOf(name) !== index);
  if (duplicates.length > 0) {
    console.log(`\n❌ DUPLICATES FOUND: ${duplicates.length}`);
    const uniqueDuplicates = [...new Set(duplicates)];
    uniqueDuplicates.forEach(name => {
      const count = countryNames.filter(n => n === name).length;
      console.log(`   - "${name}" appears ${count} times`);
    });
  } else {
    console.log('✅ No duplicates found');
  }

  // 4. Check for missing priority
  const missingPriority = data.features.filter(f => !f.properties.priority);
  if (missingPriority.length > 0) {
    console.log(`\n❌ MISSING PRIORITY: ${missingPriority.length} features`);
    missingPriority.slice(0, 10).forEach(f => {
      console.log(`   - "${f.properties.name}"`);
    });
    if (missingPriority.length > 10) {
      console.log(`   ... and ${missingPriority.length - 10} more`);
    }
  } else {
    console.log('✅ All features have priority values');
  }

  // 5. Priority distribution
  const priorityCounts = { 1: 0, 2: 0, 3: 0, other: 0 };
  data.features.forEach(f => {
    const priority = f.properties.priority;
    if (priority === 1) priorityCounts[1]++;
    else if (priority === 2) priorityCounts[2]++;
    else if (priority === 3) priorityCounts[3]++;
    else priorityCounts.other++;
  });

  console.log('\n=== PRIORITY DISTRIBUTION ===');
  console.log(`Priority 1 (major countries): ${priorityCounts[1]}`);
  console.log(`Priority 2 (medium countries): ${priorityCounts[2]}`);
  console.log(`Priority 3 (smaller countries): ${priorityCounts[3]}`);
  if (priorityCounts.other > 0) {
    console.log(`❌ Invalid priority values: ${priorityCounts.other}`);
  }

  // 6. Final validation
  console.log('\n=== FINAL VALIDATION ===');
  const isValid = (
    totalFeatures === uniqueNames.size &&
    duplicates.length === 0 &&
    missingPriority.length === 0 &&
    priorityCounts.other === 0
  );

  if (isValid) {
    console.log('✅ ALL VALIDATIONS PASSED');
    console.log(`Dataset contains ${totalFeatures} unique countries with valid priorities`);
  } else {
    console.log('❌ VALIDATION FAILED - see errors above');
    process.exit(1);
  }

} catch (error) {
  console.error('❌ ERROR:', error.message);
  process.exit(1);
}
