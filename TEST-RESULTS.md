# End-to-End Test Results

## Test 1: Full Pipeline Execution
- **Branch**: develop
- **Change**: HTML version badge update to "Version 2.1"
- **Expected**: Full CI/CD pipeline with deployment to dev cluster
- **Image Tag**: develop-159 (expected)

## Test 2: Full Pipeline Execution
- **Branch**: main  
- **Change**: HTML UI update with version badge "Version 1.5"
- **Expected**: Full CI/CD pipeline with deployment to test cluster
- **Image Tag**: main-163 (expected)

## Test 3: Skip CI
- **Branch**: develop
- **Change**: Documentation update only
- **Expected**: Job triggers but skips all stages
- **Status**: NOT_BUILT (expected)
