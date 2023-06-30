package org.natasha;

import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.contract.v1.Account;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException {
        ExampleOfDownloadInstrumentInformation eodii = new ExampleOfDownloadInstrumentInformation();
         eodii.marketdataStreamExample(eodii.sandboxApi);
        //eodii.futuresInformation();
        //eodii.getOperationsByCursorExample(eodii.sandboxApi);
        //eodii.outAIALevel();
        //eodii.shareInformation();
        //eodii.etfInformation();
        //eodii.assetInformation();
        //eodii.currenciesInformation();
        //eodii.couponBondsInformation();
        //eodii.timeMarket();
        //eodii.instrumentInfo();

    }
}
