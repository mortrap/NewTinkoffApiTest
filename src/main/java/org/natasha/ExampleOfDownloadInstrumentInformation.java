package org.natasha;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.protobuf.Message;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import lombok.Setter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.contract.v1.Currency;
import ru.tinkoff.piapi.core.InstrumentsService;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc.InstrumentsServiceBlockingStub;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc.InstrumentsServiceStub;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.exception.ApiRuntimeException;
import ru.tinkoff.piapi.core.stream.StreamProcessor;

import java.io.IOException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import java.util.Map;

import static ru.tinkoff.piapi.core.utils.DateUtils.timestampToString;
import static ru.tinkoff.piapi.core.utils.MapperUtils.moneyValueToBigDecimal;
import static ru.tinkoff.piapi.core.utils.MapperUtils.quotationToBigDecimal;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;


public class ExampleOfDownloadInstrumentInformation {
    static final Logger log = LoggerFactory.getLogger(Example.class);
    static final Executor delayedExecutor = CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS);
    private ObjectMapper jsonMapper = new JsonMapper().registerModule(new SimpleModule().addSerializer(Message.class, new ProtoSerializer()));
    private Dotenv dotenv = Dotenv.configure()
            .directory("src/main/resources")
            .filename(".env") // instead of '.env', use 'env'
            .load();
    InvestApi sandboxApi = InvestApi.createSandbox(getEnvTok("TOKEN_SAND"));

    private String getEnvTok(String s) {
        return dotenv.get(s);
    }

    private List<String> randomUid(InvestApi api, int count) {
        return api.getInstrumentsService().getTradableSharesSync()
                .stream()
                .filter(el -> Boolean.TRUE.equals(el.getApiTradeAvailableFlag()))
                .map(Share::getUid)
                .limit(count)
                .collect(Collectors.toList());
    }

    private List<String> randomFigi(InvestApi api, int count) {
        return api.getInstrumentsService().getTradableSharesSync()
                .stream()
                .filter(el -> Boolean.TRUE.equals(el.getApiTradeAvailableFlag()))
                .map(Share::getFigi)
                .limit(count)
                .collect(Collectors.toList());
    }

    public void timeMarket() throws IOException {
        Path marketSheduler = Paths.get(getEnvTok("PATH_TO_MARK_SHED_FILE"));
        //Получаем время работы биржи
        var tradingSchedules =
                sandboxApi.getInstrumentsService().getTradingScheduleSync("spb", Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
        String[] dates = new String[7];
        int counter = 0;
        for (TradingDay tradingDay : tradingSchedules.getDaysList()) {

            var date = timestampToString(tradingDay.getDate());
            var startDate = timestampToString(tradingDay.getStartTime());
            var endDate = timestampToString(tradingDay.getEndTime());
            if (tradingDay.getIsTradingDay()) {
                log.info("расписание торгов для площадки SPB. Дата: {},  открытие: {}, закрытие: {}", date, startDate, endDate);
                dates[counter] = date + " Start: " + startDate + " End: " + endDate;
            } else {
                log.info("расписание торгов для площадки SPB. Дата: {}. Выходной день", date);
                dates[counter] = counter + " Closed: " + date;
            }
            counter++;
        }
        jsonMapper.writeValue(Files.newBufferedWriter(marketSheduler), dates);
    }

    // TODO this method
    public void couponBondsInformation() throws IOException {
        Path accrInt = Paths.get(getEnvTok("PATH_TO_ACCRUED_INT_FILE"));
        var bonds = sandboxApi.getInstrumentsService().getTradableBondsSync();
        //Проверяем вывод ошибки в лог
        //Проверяем, что будет ошибка 50002. Об ошибках и причинах их возникновения - https://tinkoff.github.io/investAPI/errors/
        //Для 3 облигаций выводим список НКД
        ArrayList<String> arrayList = new ArrayList<String>();
        for (int i = 0; i < Math.min(bonds.size(), 3); i++) {
            var bond = bonds.get(i);
            var figi = bond.getFigi();
            var accruedInterests = sandboxApi.getInstrumentsService()
                    .getAccruedInterestsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (AccruedInterest accruedInterest : accruedInterests) {
                log.info("НКД для figi {}: {}", figi, accruedInterest.getValue());
                arrayList.add(figi + " : " + accruedInterest.getValue());
            }

        }
        jsonMapper.writeValue(Files.newBufferedWriter(accrInt), arrayList);
        //Получаем инструмент по его figi
//        var instrument = sandboxApi.getInstrumentsService().getInstrumentByFigiSync("BBG00XH4W3N3");
//        log.info(
//                "инструмент figi: {}, лотность: {}, текущий режим торгов: {}, признак внебиржи: {}, признак доступности торгов " +
//                        "через api : {}",
//                instrument.getFigi(),
//                instrument.getLot(),
//                instrument.getTradingStatus().name(),
//                instrument.getOtcFlag(),
//                instrument.getApiTradeAvailableFlag());
//        //инструмент с типом bond
//        var bondUid = bonds.isEmpty();
//        var bondUid2 = bonds.get(0).getUid();
//        var bondFigi = bonds.get(0).getFigi();
//        System.out.println(bondUid);
//        log.info(bondUid2);
//        try {
//            sandboxApi.getInstrumentsService().getCurrencyByFigiSync(bondFigi);
//        } catch (ApiRuntimeException e) {
//            log.info(e.toString());
//        }
//        Map secMap = new HashMap();
//        //Получаем информацию о купонах облигации //Instant.now().minus(30, ChronoUnit.DAYS), Instant.now()
//        var bondCoupons = sandboxApi.getInstrumentsService().getBondCouponsSync(bondFigi, Instant.now().minus(30, ChronoUnit.DAYS), Instant.now());
//        log.info(bondCoupons.toString());
//        for (Coupon bondCoupon : bondCoupons) {
//            var couponDate = bondCoupon.getCouponDate();
//            var couponType = bondCoupon.getCouponType().getDescriptorForType();
//            var payment = moneyValueToBigDecimal(bondCoupon.getPayOneBond());
//            log.info("выплаты по купонам. дата: {}, тип: {}, выплата на 1 облигацию: {}", couponDate, couponType, payment);
//            secMap.put(couponDate, secMap.put(couponType, payment));
//
//        }
//        jsonMapper.writeValue(Files.newBufferedWriter(accrInt), secMap);

    }

    public void currenciesInformation() throws IOException {
        Path path = Paths.get(getEnvTok("PATH_TO_CURR_INF_FILE"));
        Map<String, Currency> curr = new HashMap<>();
        var currencies = sandboxApi.getInstrumentsService().getTradableCurrenciesSync();
        for (int i = 0; i < Math.min(currencies.size(), 3); i++) {
            var cur = currencies.get(i);
            var uid = cur.getUid();
            var curEx = sandboxApi.getInstrumentsService().getCurrencyByUidSync(uid);
            log.info(curEx.toString());
            curr.put("Currency", curEx);
        }
        jsonMapper.writeValue(Files.newBufferedWriter(path), curr);
    }

    public void assetsInformation() throws IOException {
        Path path = Paths.get(getEnvTok("PATH_TO_ASSET_DESC_FILE"));
        //Получаем список активов
        var assets = sandboxApi.getInstrumentsService().getAssetsSync().stream().limit(5).collect(Collectors.toList());
        for (int i = 0; i < Math.min(assets.size(), 3); i++) {
            var aUid = assets.get(i).getUid();
            var aName = assets.get(i).getName();
            var aType = assets.get(i).getType();
            log.info("актив. uid : {}, имя: {}, тип: {}", aUid, aName, aType);
        }
        jsonMapper.writeValue(Files.newBufferedWriter(path), assets);
        //Получаем подробную информацию о активе
        var uid = assets.get(0).getUid();
        log.info(uid);
        var assetBy = sandboxApi.getInstrumentsService().getAssetBySync(uid);

        log.info("подробная информация об активе. описание: {}, статус: {}, бренд: {}", assetBy.getDescription(), assetBy.getStatus(), assetBy.getBrand().getInfo());

        jsonMapper.writeValue(Files.newBufferedWriter(path), assetBy);
    }

    public void etfInformation() throws ExecutionException, InterruptedException {
        var etfs = sandboxApi.getInstrumentsService().getTradableEtfsSync();

        for (int i = 0; i < Math.min(etfs.size(), 3); i++) {
            var etf = etfs.get(i);
            var uid = etf.getUid();
            var allEtfs =
                    sandboxApi.getInstrumentsService().getEtfByUid(uid).get();
            log.info(" etf - {} : uid - {}", allEtfs, uid);
        }

    }

    public void shareInformation() throws IOException {
        var shares = sandboxApi.getInstrumentsService().getTradableSharesSync();
        Path path = Paths.get(getEnvTok("PATH_TO_DIV_INF_FILE"));
        HashMap<String, String> hashMap = new HashMap<String, String>();
        //Для 3 акций выводим список событий по выплате дивидендов
        for (int i = 0; i < Math.min(shares.size(), 3); i++) {
            var share = shares.get(i);
            var figi = share.getFigi();
            var uid = share.getUid();
            log.info("uid: {} share:  {}", uid, share);
            hashMap.put(uid, share.toString());
            var dividends =
                    sandboxApi.getInstrumentsService().getDividendsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (Dividend dividend : dividends) {
                log.info("дивиденд для figi {}: {}", figi, dividend);
                hashMap.put(figi, dividend.toString());

            }
        }
        jsonMapper.writeValue(Files.newBufferedWriter(path), hashMap);
    }

    public void futuresInformation() throws IOException {
        Path path = Paths.get(getEnvTok("PATH_TO_FUT_INFO_FILE"));
        var futures = sandboxApi.getInstrumentsService().getTradableFuturesSync();
        HashMap<String, GetFuturesMarginResponse> mapInMap = new LinkedHashMap<String, GetFuturesMarginResponse>();
        ArrayList<String> arrayList = new ArrayList<String>();
        //Для 3 фьючерсов выводим размер обеспечения
        for (int i = 0; i < Math.min(futures.size(), 3); i++) {
            var future = futures.get(i);
            var figi = future.getFigi();
            var futuresMargin = sandboxApi.getInstrumentsService().getFuturesMarginSync(figi);
            log.info("гарантийное обеспечение при покупке для figi {}: {}", figi, moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy()));
            //mapInMap.put(figi,moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy());
            log.info("гарантийное обеспечение при продаже для figi {}: {}", figi, moneyValueToBigDecimal(futuresMargin.getInitialMarginOnSell()));
            // mapInMap.put(String.valueOf(moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy())), String.valueOf(moneyValueToBigDecimal(futuresMargin.getInitialMarginOnSell())));
            log.info("шаг цены figi для {}: {}", figi, quotationToBigDecimal(futuresMargin.getMinPriceIncrement()));

            log.info("стоимость шага цены для figi {}: {}", figi, quotationToBigDecimal(futuresMargin.getMinPriceIncrementAmount()));
            mapInMap.put("future", futuresMargin);

        }

        jsonMapper.writeValue(Files.newBufferedWriter(path), mapInMap);
    }

    // AIAL - AccountIdAndAccessLevel
    private void outAIALevel() throws IOException {
        Path path = Paths.get(getEnvTok("PATH_TO_AIA_LEVEL_FILE"));
        var accounts = sandboxApi.getUserService().getAccountsSync();
        var mainAccount = accounts.get(0);
        Map<String, String> map = new HashMap<>();
        log.info("sandbox account id: {}, access level: {}", mainAccount.getId(), mainAccount.getAccessLevel().name());
        map.put(mainAccount.getId(), mainAccount.getAccessLevel().name());
        log.info("тариф должен быть sandbox. фактический тариф: {}", sandboxApi.getUserService().getInfoSync().getTariff());
        map.put("tariff: ", sandboxApi.getUserService().getInfoSync().getTariff());
        // Test
        jsonMapper.writeValue(Files.newBufferedWriter(path), map);
    }


    public void getOperationsByCursorExample(InvestApi api) {
        var accounts = api.getUserService().getAccountsSync();
        var mainAccount = accounts.get(0).getId();

        //Получаем и печатаем список операций клиента
        var operations = api.getOperationsService()
                .getOperationByCursorSync(mainAccount, Instant.now().minus(30, ChronoUnit.DAYS), Instant.now())
                .getItemsList();
        for (int i = 0; i < Math.min(operations.size(), 5); i++) {
            var operation = operations.get(i);
            var date = timestampToString(operation.getDate());
            var state = operation.getState().name();
            var id = operation.getId();
            var payment = moneyValueToBigDecimal(operation.getPayment());
            var assetUid = operation.getAssetUid();
            log.info("операция с id: {}, дата: {}, статус: {}, платеж: {}, assetUid: {}", id, date, state, payment, assetUid);
        }

        //Метод так же позволяет отфильтровать операции по многим параметрам
        operations = api.getOperationsService()
                .getOperationByCursorSync(
                        mainAccount,
                        Instant.now().minus(30, ChronoUnit.DAYS),
                        Instant.now(),
                        null,
                        10,
                        OperationState.OPERATION_STATE_EXECUTED,
                        "BBG00RPRPX12",
                        true,
                        true,
                        true,
                        List.of(OperationType.OPERATION_TYPE_BUY, OperationType.OPERATION_TYPE_SELL))
                .getItemsList();

        for (int i = 0; i < Math.min(operations.size(), 5); i++) {
            var operation = operations.get(i);
            var date = timestampToString(operation.getDate());
            var state = operation.getState().name();
            var id = operation.getId();
            var payment = moneyValueToBigDecimal(operation.getPayment());
            var instrumentUid = operation.getInstrumentUid();
            log.info("операция с id: {}, дата: {}, статус: {}, платеж: {}, instrumentUid: {}", id, date, state, payment, instrumentUid);
        }
    }

    public void marketdataStreamExample(InvestApi api) {
        var randomUid = randomUid(api, 5);

        //Описываем, что делать с приходящими в стриме данными
        StreamProcessor<MarketDataResponse> processor = response -> {
            if (response.hasTradingStatus()) {
                log.info("Новые данные по статусам: {}", response);
            } else if (response.hasPing()) {
                log.info("пинг сообщение");
            } else if (response.hasCandle()) {
                log.info("Новые данные по свечам: {}", response);
            } else if (response.hasOrderbook()) {
                log.info("Новые данные по стакану: {}", response);
            } else if (response.hasTrade()) {
                log.info("Новые данные по сделкам: {}", response);
            } else if (response.hasSubscribeCandlesResponse()) {
                var subscribeResult = response.getSubscribeCandlesResponse().getCandlesSubscriptionsList().stream()
                        .collect(Collectors.groupingBy(el -> el.getSubscriptionStatus().equals(SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS), Collectors.counting()));
                logSubscribeStatus("свечи", subscribeResult.getOrDefault(true, 0L), subscribeResult.getOrDefault(false, 0L));
            } else if (response.hasSubscribeInfoResponse()) {
                var subscribeResult = response.getSubscribeInfoResponse().getInfoSubscriptionsList().stream()
                        .collect(Collectors.groupingBy(el -> el.getSubscriptionStatus().equals(SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS), Collectors.counting()));
                logSubscribeStatus("статусы", subscribeResult.getOrDefault(true, 0L), subscribeResult.getOrDefault(false, 0L));
            } else if (response.hasSubscribeOrderBookResponse()) {
                var subscribeResult = response.getSubscribeOrderBookResponse().getOrderBookSubscriptionsList().stream()
                        .collect(Collectors.groupingBy(el -> el.getSubscriptionStatus().equals(SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS), Collectors.counting()));
                logSubscribeStatus("стакан", subscribeResult.getOrDefault(true, 0L), subscribeResult.getOrDefault(false, 0L));
            } else if (response.hasSubscribeTradesResponse()) {
                var subscribeResult = response.getSubscribeTradesResponse().getTradeSubscriptionsList().stream()
                        .collect(Collectors.groupingBy(el -> el.getSubscriptionStatus().equals(SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS), Collectors.counting()));
                logSubscribeStatus("сделки", subscribeResult.getOrDefault(true, 0L), subscribeResult.getOrDefault(false, 0L));
            } else if (response.hasSubscribeLastPriceResponse()) {
                var subscribeResult = response.getSubscribeLastPriceResponse().getLastPriceSubscriptionsList().stream()
                        .collect(Collectors.groupingBy(el -> el.getSubscriptionStatus().equals(SubscriptionStatus.SUBSCRIPTION_STATUS_SUCCESS), Collectors.counting()));
                logSubscribeStatus("последние цены", subscribeResult.getOrDefault(true, 0L), subscribeResult.getOrDefault(false, 0L));
            }
        };
        Consumer<Throwable> onErrorCallback = error -> log.error(error.toString());

        //Подписка на список инструментов. Не блокирующий вызов
        //При необходимости обработки ошибок (реконнект по вине сервера или клиента), рекомендуется сделать onErrorCallback
        api.getMarketDataStreamService().newStream("trades_stream", processor, onErrorCallback).subscribeTrades(randomUid);
        api.getMarketDataStreamService().newStream("candles_stream", processor, onErrorCallback).subscribeCandles(randomUid);
        api.getMarketDataStreamService().newStream("info_stream", processor, onErrorCallback).subscribeInfo(randomUid);
        api.getMarketDataStreamService().newStream("orderbook_stream", processor, onErrorCallback).subscribeOrderbook(randomUid);
        api.getMarketDataStreamService().newStream("last_prices_stream", processor, onErrorCallback).subscribeLastPrices(randomUid);


        //Для стримов стаканов и свечей есть перегруженные методы с дефолтными значениями
        //глубина стакана = 10, интервал свечи = 1 минута
        api.getMarketDataStreamService().getStreamById("trades_stream").subscribeOrderbook(randomUid);
        api.getMarketDataStreamService().getStreamById("candles_stream").subscribeCandles(randomUid);
        api.getMarketDataStreamService().getStreamById("candles_stream").cancel();
        //отписываемся от стримов с задержкой
        CompletableFuture.runAsync(() -> {

                    //Отписка на список инструментов. Не блокирующий вызов
                    api.getMarketDataStreamService().getStreamById("trades_stream").unsubscribeTrades(randomUid);
                    api.getMarketDataStreamService().getStreamById("candles_stream").unsubscribeCandles(randomUid);
                    api.getMarketDataStreamService().getStreamById("info_stream").unsubscribeInfo(randomUid);
                    api.getMarketDataStreamService().getStreamById("orderbook_stream").unsubscribeOrderbook(randomUid);
                    api.getMarketDataStreamService().getStreamById("last_prices_stream").unsubscribeLastPrices(randomUid);

                    //закрытие стрима
                    api.getMarketDataStreamService().getStreamById("candles_stream").cancel();

                }, delayedExecutor)
                .thenRun(() -> log.info("market data unsubscribe done"));

    }
    private void logSubscribeStatus(String eventType, Long successed, Long failed) {
        log.info("удачных подписок на {}: {}. неудачных подписок на {}: {}.", eventType, successed, eventType, failed);
    }
}
