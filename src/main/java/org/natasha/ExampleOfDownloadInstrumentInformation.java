package org.natasha;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Getter;
import lombok.Setter;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.InstrumentsService;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc.InstrumentsServiceBlockingStub;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc.InstrumentsServiceStub;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.exception.ApiRuntimeException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.json.simple.JSONObject;
import org.json.simple.ItemList;

import static ru.tinkoff.piapi.core.utils.DateUtils.timestampToString;
import static ru.tinkoff.piapi.core.utils.MapperUtils.moneyValueToBigDecimal;
import static ru.tinkoff.piapi.core.utils.MapperUtils.quotationToBigDecimal;

@Getter
@Setter
public class ExampleOfDownloadInstrumentInformation {
    static final Logger log = LoggerFactory.getLogger(Example.class);
    //String token = "";
    static JSONObject jsonObject;
    static JSONArray jsonArray = new JSONArray();
    static InvestApi sandboxApi = InvestApi.createSandbox(getTok());

    private static String getTok() {
        String fileName = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/tts.txt"; // путь к файлу
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            for (int i = 0; i < 2; i++) {
                lines.add(reader.readLine());
            }
            //System.out.println(lines);
        } catch (IOException e) {
            System.err.println("Ошибка при чтении файла: " + e.getMessage());
        }
        return lines.get(1);

    }
    public static void main(String[] args) throws IOException {
        //var accountId = sandboxApi.getSandboxService().openAccountSync();
        //log.info("открыт новый аккаунт в песочнице {}", accountId);
        System.out.println(sandboxApi.isSandboxMode());
        // Test board : =>
        shareInformation();
        //futuresInformation();
        //assetsInformation();
        //timeMarket();
        //couponBondsInformation();
        //currenciesInformation();
        System.out.println(randomFigi(sandboxApi, 2));
        //sandboxApi.getSandboxService().payIn(mainAccount.getId(), MoneyValue.newBuilder().setUnits(10000).setCurrency("RUB").build());
        //sandboxApi.getSandboxService().payIn(mainAccount.getId(), MoneyValue.newBuilder().setUnits(10000).setCurrency("USD").build());
    }

    private static List<String> randomFigi(InvestApi api, int count) {
        return api.getInstrumentsService().getTradableSharesSync()
                .stream()
                .filter(el -> Boolean.TRUE.equals(el.getApiTradeAvailableFlag()))
                .map(Share::getFigi)
                .limit(count)
                .collect(Collectors.toList());
    }

    private static void timeMarket() {
        String marketSheduler = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/marketSheduler.json";
        //Получаем время работы биржи
        var tradingSchedules =
                sandboxApi.getInstrumentsService().getTradingScheduleSync("spb", Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
        String[] dates = new String[7];
        int counter = 0;
        for (TradingDay tradingDay : tradingSchedules.getDaysList()) {
            jsonObject = new JSONObject();
            var date = timestampToString(tradingDay.getDate());
            var startDate = timestampToString(tradingDay.getStartTime());
            var endDate = timestampToString(tradingDay.getEndTime());
            if (tradingDay.getIsTradingDay()) {
                log.info("расписание торгов для площадки SPB. Дата: {},  открытие: {}, закрытие: {}", date, startDate, endDate);
                dates[counter] = date + " " + startDate + " " + endDate;
            } else {
                log.info("расписание торгов для площадки SPB. Дата: {}. Выходной день", date);
                dates[counter] = date;
            }
            counter++;
        }
        System.out.println(Arrays.toString(dates));
        for (String elem : dates) {
            jsonObject.put(counter, elem + "\n");
            counter--;
        }
// доделать json newline write
        writeJson(marketSheduler, jsonObject);
    }
// TODO this method
    static private void couponBondsInformation() throws IOException {
        String forFile = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/accruedInterests.json";
        var bonds = sandboxApi.getInstrumentsService().getTradableBondsSync();
        jsonObject = new JSONObject();
        //Проверяем вывод ошибки в лог
        //Проверяем, что будет ошибка 50002. Об ошибках и причинах их возникновения - https://tinkoff.github.io/investAPI/errors/
        //Для 3 облигаций выводим список НКД
        Map jsonMap = new HashMap();
        for (int i = 0; i < Math.min(bonds.size(), 3); i++) {
            var bond = bonds.get(i);
            var figi = bond.getFigi();
            var accruedInterests = sandboxApi.getInstrumentsService()
                    .getAccruedInterestsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (AccruedInterest accruedInterest : accruedInterests) {
                log.info("НКД для figi {}: {}", figi, accruedInterest.getValue());
                jsonObject.put(figi, accruedInterest.getValue());
            }

        }
        //Получаем инструмент по его figi
        var instrument = sandboxApi.getInstrumentsService().getInstrumentByFigiSync("BBG00XH4W3N3");
        log.info(
                "инструмент figi: {}, лотность: {}, текущий режим торгов: {}, признак внебиржи: {}, признак доступности торгов " +
                        "через api : {}",
                instrument.getFigi(),
                instrument.getLot(),
                instrument.getTradingStatus().name(),
                instrument.getOtcFlag(),
                instrument.getApiTradeAvailableFlag());
        //инструмент с типом bond
        var bondUid = bonds.isEmpty();
        var bondUid2 = bonds.get(0).getUid();
        var bondFigi = bonds.get(0).getFigi();
        System.out.println(bondUid);
        log.info(bondUid2);
        try {
            sandboxApi.getInstrumentsService().getCurrencyByFigiSync(bondFigi);
        } catch (ApiRuntimeException e) {
            log.info(e.toString());
        }
        //??

        Map secMap = new HashMap();
        //Получаем информацию о купонах облигации //Instant.now().minus(30, ChronoUnit.DAYS), Instant.now()
        var bondCoupons = sandboxApi.getInstrumentsService().getBondCouponsSync(bondFigi, Instant.now().minus(30, ChronoUnit.DAYS), Instant.now());
        log.info(bondCoupons.toString());
        for (Coupon bondCoupon : bondCoupons) {
            var couponDate = bondCoupon.getCouponDate();
            var couponType = bondCoupon.getCouponType().getDescriptorForType();
            var payment = moneyValueToBigDecimal(bondCoupon.getPayOneBond());
            log.info("выплаты по купонам. дата: {}, тип: {}, выплата на 1 облигацию: {}", couponDate, couponType, payment);
            jsonObject.put(couponDate, secMap.put(couponType, payment));

        }
        writeJson(forFile, jsonObject);

    }

    static void writeJson(String path, JSONObject jsonObject) {
        try {
            FileWriter file = new FileWriter(path);
            file.write(jsonObject.toJSONString());
            file.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static void currenciesInformation() {
        jsonObject = new JSONObject();
        String path = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/currenciesInformation.json";
        var currencies = sandboxApi.getInstrumentsService().getTradableCurrenciesSync();
        for (int i = 0; i < Math.min(currencies.size(), 3); i++) {
            var cur = currencies.get(i);
            var uid = cur.getUid();
            var curEx = sandboxApi.getInstrumentsService().getCurrencyByUidSync(uid);
            log.info(curEx.toString());
            jsonObject.put("Currency", curEx);
        }
        writeJson(path, jsonObject);
    }
    private static void assetsInformation() {
        jsonObject = new JSONObject();
        String path = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/assetList.json";
        //Получаем список активов
        var assets = sandboxApi.getInstrumentsService().getAssetsSync().stream().limit(5).collect(Collectors.toList());
        for (int i = 0; i < Math.min(assets.size(), 3); i++) {
            var aUid = assets.get(i).getUid();
            var aName = assets.get(i).getName();
            var aType = assets.get(i).getType();
            log.info("актив. uid : {}, имя: {}, тип: {}", aUid, aName, aType);
            jsonObject.put(assets.get(i).toString(), aUid.toString());
            jsonObject.put(assets.get(i), aName);
            jsonObject.put(assets.get(i), aType);
        }
        writeJson(path, jsonObject);
        jsonObject = new JSONObject();
        String assetDescriptions = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/assetDescription.json";
        //Получаем подробную информацию о активе
        var uid = assets.get(0).getUid();
        log.info(uid);
        var assetBy = sandboxApi.getInstrumentsService().getAssetBySync(uid);

        log.info("подробная информация об активе. описание: {}, статус: {}, бренд: {}", assetBy.getDescription(), assetBy.getStatus(), assetBy.getBrand().getInfo());
        jsonObject.put(assetBy, assetBy.getDescription());
        jsonObject.put(assetBy, assetBy.getStatus());
        jsonObject.put(assetBy, assetBy.getBrand().getInfo());
        writeJson(assetDescriptions, jsonObject);
    }

    private void etfInformation() {
        var etfs = sandboxApi.getInstrumentsService().getTradableEtfsSync();
        for (int i = 0; i < Math.min(etfs.size(), 3); i++) {

            var etf = etfs.get(i);
            var uid = etf.getUid();
            var allEtfs =
                    sandboxApi.getInstrumentsService().getEtfByPositionUid(uid);
            log.info(" etf - {} : uid - {}", allEtfs, uid);
        }
    }

    private static void shareInformation() throws IOException {
        var shares = sandboxApi.getInstrumentsService().getTradableSharesSync();

        var path = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/dividendsInformation.json";
        ObjectMapper objectMapper = new ObjectMapper();
        HashMap<String, String> hashMap = new HashMap<String, String>();
        FileWriter fileWriter = new FileWriter(path);
        //Для 3 акций выводим список событий по выплате дивидендов
        for (int i = 0; i < Math.min(shares.size(), 3); i++) {
            var share = shares.get(i);
            var figi = share.getFigi();
            var uid = share.getUid();
            hashMap.put(uid, share.toString());
            var dividends =
                    sandboxApi.getInstrumentsService().getDividendsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (Dividend dividend : dividends) {
                log.info("дивиденд для figi {}: {}", figi, dividend);
                hashMap.put(figi, dividend.toString());

            }
        }
        objectMapper.writeValue(fileWriter, hashMap);
    }

    private static void futuresInformation() throws IOException {
        var fInfo = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/futuresInformation.json";
        ObjectMapper objectMapper = new ObjectMapper();
        var futures = sandboxApi.getInstrumentsService().getTradableFuturesSync();
        FileWriter file = new FileWriter(fInfo);
        HashMap<String, String> map = new LinkedHashMap<String, String>();
        //HashMap<String, String> mapInMap = new LinkedHashMap<String, String>();
        ArrayList<String> arrayList = new ArrayList<String>();
        //Для 3 фьючерсов выводим размер обеспечения
        for (int i = 0; i < Math.min(futures.size(), 3); i++) {
            var future = futures.get(i);
            var figi = future.getFigi();
            var futuresMargin = sandboxApi.getInstrumentsService().getFuturesMarginSync(figi);
            log.info("гарантийное обеспечение при покупке для figi {}: {}", figi, moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy()));
            String marginOnBuy = String.valueOf(moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy()));
            arrayList.add("\n гарантийное обеспечение при покупке: " + marginOnBuy);
            log.info("гарантийное обеспечение при продаже для figi {}: {}", figi, moneyValueToBigDecimal(futuresMargin.getInitialMarginOnSell()));
            String marginOnSell = String.valueOf(moneyValueToBigDecimal(futuresMargin.getInitialMarginOnSell()));
            arrayList.add("\n гарантийное обеспечение при продаже: " + marginOnSell);
            log.info("шаг цены figi для {}: {}", figi, quotationToBigDecimal(futuresMargin.getMinPriceIncrement()));
            String minPriceIncrement = String.valueOf(quotationToBigDecimal(futuresMargin.getMinPriceIncrement()));
            arrayList.add("\n шаг цены: " + minPriceIncrement);
            log.info("стоимость шага цены для figi {}: {}", figi, quotationToBigDecimal(futuresMargin.getMinPriceIncrementAmount()));
            String minPriceIncrementAmount = String.valueOf(quotationToBigDecimal(futuresMargin.getMinPriceIncrementAmount()));
            arrayList.add("\n стоимость шага цены: " + minPriceIncrementAmount);

//            map.put(figi, marginOnBuy);
//            map.put(marginOnBuy, marginOnSell);
//            map.put(minPriceIncrement, minPriceIncrementAmount);
        }
        objectMapper.writeValue(file, arrayList);
    }

    // AIA - AccountIdAndAccessLevel
    private static void outAIALevel() {
        String outputFileName = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/outAccountIdAndAccessLevel.json";
        var accounts = sandboxApi.getUserService().getAccountsSync();
        var mainAccount = accounts.get(0);
        jsonObject = new JSONObject();
        log.info("sandbox account id: {}, access level: {}", mainAccount.getId(), mainAccount.getAccessLevel().name());
        jsonObject.put(mainAccount.getId(), mainAccount.getAccessLevel().name());
        log.info("тариф должен быть sandbox. фактический тариф: {}", sandboxApi.getUserService().getInfoSync().getTariff());
        jsonObject.put("tariff: ", sandboxApi.getUserService().getInfoSync().getTariff());
        // Test
        writeJson(outputFileName, jsonObject);
    }

}
