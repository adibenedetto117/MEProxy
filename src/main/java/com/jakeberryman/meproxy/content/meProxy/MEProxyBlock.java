package com.jakeberryman.meproxy.content.meProxy;

import appeng.block.AEBaseEntityBlock;

public class MEProxyBlock extends AEBaseEntityBlock<MEProxyBlockEntity> {
    public MEProxyBlock() {
        super(metalProps().noOcclusion());
    }
}
