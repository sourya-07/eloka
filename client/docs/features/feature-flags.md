# ✅ Toggle Feature Flags

Feature flags are used to enable/disable a specific feature, permanently or conditionally.

Features can also be allowed for specific environments, user-types, user-ids, etc. Especially helpful for experimental features.

## 🛠️ Implementation
- To add a new feature-flag, add a new entry in the [src/constants/featureFlags.ts](src/constants/featureFlags.ts) file.
- To test for a feature-flag, use the `useFeatureFlag` hook from [src/hooks/useFeatureFlag.tsx](src/hooks/useFeatureFlag.tsx).
  - Eg: `const [isFeatureEnabled] = useFeatureFlag('MY_FEATURE');`
  - to dynamically check for feature flags, use the `checkFeatureFlag` function:
	```jsx
	import { useFeatureFlag } from 'src/hooks/useFeatureFlag';
	const [_isFeatureEnabled, checkFeatureFlag] = useFeatureFlag();

	const isAnotherFeatureEnabled = checkFeatureFlag('ANOTHER_FEATURE');
	```
  - You can invert the flag check by prefixing the flag key with `!`:
	```jsx
	const isFeatureDisabled = checkFeatureFlag('!MY_FEATURE');
	```
- Feature flags can also depend on other feature flags. This is done using the `requiredFeatures` array in the feature flag definition.
  - Eg:
	```ts
	export const featureFlags = {
	  FEATURE_A: {
	    key: 'FEATURE_A',
	    description: 'Feature A',
	  },
	  FEATURE_B: {
	    key: 'FEATURE_B',
	    description: 'Feature B',
	    requiredFeatures: ['FEATURE_A'], // FEATURE_B depends on FEATURE_A
	  },
	};
	```
  - In the above example, `FEATURE_B` will only be enabled if `FEATURE_A` is also enabled.
- Feature flags can be enabled/disabled for specific environments using the `environments` array in the feature flag definition.
- Feature flags can be enabled/disabled for specific user types using the `allowedUserTypes` array in the feature flag definition.
- Feature flags can be enabled/disabled for specific user ids using the `allowedUserIds` array in the feature flag definition.
- Feature flags can be enabled/disabled for specific org ids using the `allowedOrgIds` array in the feature flag definition.

## TODO
- Provide for A-B testing, gradual roll-out, etc.