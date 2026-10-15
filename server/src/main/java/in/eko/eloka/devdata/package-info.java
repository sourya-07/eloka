/**
 * Local-only developer conveniences: the dummy-data seeder and a reset endpoint. Every bean
 * here is {@code @Profile("local")} and {@code UnsafeInProduction}.
 */
@ApplicationModule(displayName = "Dev data (local only)")
package in.eko.eloka.devdata;

import org.springframework.modulith.ApplicationModule;
