package com.lura.lambda;

import com.lura.core.api.SoundPlayResponse;
import com.lura.core.catalog.SoundCatalogItem;

interface PlaybackUrlProvider {

    SoundPlayResponse getPlayUrl(SoundCatalogItem sound, String objectKey);
}
