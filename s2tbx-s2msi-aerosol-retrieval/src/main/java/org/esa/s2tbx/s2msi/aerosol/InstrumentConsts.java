/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.esa.s2tbx.s2msi.aerosol;

import org.esa.s2tbx.s2msi.idepix.util.S2IdepixConstants;

/**
 * Instrument specific constants
 *
 * @author olafd
 */
class InstrumentConsts {

    public static final String MSI_INSTRUMENT_NAME = "MSI";

    public static final String[] REFLEC_NAMES = S2IdepixConstants.S2_MSI_REFLECTANCE_BAND_NAMES;

    public static final String[] GEOM_NAMES = {
            S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[0],
            S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[2],
            S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[1],
            S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[3]
    };

    public static final String IDEPIX_FLAG_BAND_NAME = "pixel_classif_flags";

    public static final String VALID_RETRIEVAL_EXPRESSION =
                        IDEPIX_FLAG_BAND_NAME + ".IDEPIX_LAND "
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLEAR_SNOW "
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLOUD "   // ???
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLOUD_BUFFER "   // ???
            + " && (" + S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[0] + "<70)";

    public static final String VALID_AOT_OUT_EXPRESSION =
                    IDEPIX_FLAG_BAND_NAME + ".IDEPIX_LAND "
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLEAR_SNOW "
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLOUD "   // ???
            + " && !" + IDEPIX_FLAG_BAND_NAME + ".IDEPIX_CLOUD_BUFFER "   // ???
            + " && (" + S2IdepixConstants.S2_MSI_ANNOTATION_BAND_NAMES[0] + "<70)";

    public static final String OZONE_NAME = "tco3";
    public static final String SURFACE_PRESSURE_NAME = "sp";
    public static final String ELEVATION_NAME = "elevation";
    public static final String WATER_VAPOUR_NAME = "water_vapour";
    public static final String AEROSOL_TYPE_NAME = "aerosol_type";

    // todo: normalize these elsewhere, see old code
    public static final double[] FIT_WEIGHTS = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0};

    public static final int N_LUT_BANDS = 13;


}
