# L1C SYN Tool
Sentinel-3 OLCI/SLSTR L1C Synergy Tool for S3TBX in SNAP


## IDE configuration
### IntelliJ IDEA
* Choose **Jar Application** runner
* Config:
  * Path to Jar: `<SNAP_INSTALL_DIR>\snap\modules\ext\org.esa.snap.snap-rcp\org-esa-snap\snap-main.jar`
  * VM options: `-Dsnap.debug=true -Dsun.java2d.noddraw=true -Dsun.awt.nopixfmt=true -Dsun.java2d.dpiaware=false -Dorg.netbeans.level=INFO -Xmx10G -Dsnap.jai.tileCacheSize=4000`
  * Program Arguments: `--clusters "<PROJECT_DIR>/s3tbx-l1csyn-op/target/nbm/netbeans/s3tbx" --patches "<PROJECT_DIR>/s3tbx-l1csyn-op/$/target/classes" --userdir "<USER_DIR>\AppData\Roaming\SNAP"`
  * Working Dir: `<SNAP_INSTALL_DIR>`
  
 ## Changelog
 Changes are tracked in the [issue tracker](https://github.com/bcdev/l1c-syn-tool/issues) of GitHub. 
 Have also a look at the [release page](https://github.com/bcdev/l1c-syn-tool/releases) 