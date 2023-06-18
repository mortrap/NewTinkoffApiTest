package org.natasha;

import lombok.Getter;
import lombok.Setter;
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
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ru.tinkoff.piapi.core.utils.DateUtils.timestampToString;
import static ru.tinkoff.piapi.core.utils.MapperUtils.moneyValueToBigDecimal;
import static ru.tinkoff.piapi.core.utils.MapperUtils.quotationToBigDecimal;

@Getter
@Setter
public class ExampleOfDownloadInstrumentInformation {
    static final Logger log = LoggerFactory.getLogger(Example.class);
    String token = "";

    static InvestApi sandboxApi = InvestApi.createSandbox(getTok());

    private static String getTok() {
        String fileName = "/home/morbro/IdeaProjects/NewTinkoffApiTest/src/main/resources/tts.txt"; // путь к файлу
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            for (int i = 0; i < 2; i++) {
                lines.add(reader.readLine());
            }

            System.out.println(lines); // выводим строку на экран
        } catch (IOException e) {
            System.err.println("Ошибка при чтении файла: " + e.getMessage());
        }
        return lines.get(1);

    }

    public static void main(String[] args) {
        System.out.println(sandboxApi.isSandboxMode());
        //var accountId = sandboxApi.getSandboxService().openAccountSync();
        //log.info("открыт новый аккаунт в песочнице {}", accountId);

        var accounts = sandboxApi.getUserService().getAccountsSync();
        var mainAccount = accounts.get(0);
        log.info("sandbox account id: {}, access level: {}", mainAccount.getId(), mainAccount.getAccessLevel().name());
        log.info("тариф должен быть sandbox. фактический тариф: {}", sandboxApi.getUserService().getInfoSync().getTariff());

        currenciesInformation();
        //var shares = sandboxApi.getInstrumentsService().getTradableSharesSync();
        //var etfs = sandboxApi.getInstrumentsService().getTradableEtfsSync();
        //var bonds = sandboxApi.getInstrumentsService().getTradableBondsSync();
        //var futures = sandboxApi.getInstrumentsService().getTradableFuturesSync();
        //var currencies = sandboxApi.getInstrumentsService().getTradableCurrenciesSync();


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

    private void timeMarket() {
        //Получаем время работы биржи
        var tradingSchedules =
                sandboxApi.getInstrumentsService().getTradingScheduleSync("spb", Instant.now(), Instant.now().plus(5, ChronoUnit.DAYS));
        for (TradingDay tradingDay : tradingSchedules.getDaysList()) {
            var date = timestampToString(tradingDay.getDate());
            var startDate = timestampToString(tradingDay.getStartTime());
            var endDate = timestampToString(tradingDay.getEndTime());
            if (tradingDay.getIsTradingDay()) {
                log.info("расписание торгов для площадки SPB. Дата: {},  открытие: {}, закрытие: {}", date, startDate, endDate);
            } else {
                log.info("расписание торгов для площадки SPB. Дата: {}. Выходной день", date);
            }
        }
    }

    private void couponBondsInformation() {
        var bonds = sandboxApi.getInstrumentsService().getTradableBondsSync();

        //Проверяем вывод ошибки в лог
        //Проверяем, что будет ошибка 50002. Об ошибках и причинах их возникновения - https://tinkoff.github.io/investAPI/errors/
        //Для 3 облигаций выводим список НКД
        for (int i = 0; i < Math.min(bonds.size(), 3); i++) {
            var bond = bonds.get(i);
            var figi = bond.getFigi();
            var accruedInterests = sandboxApi.getInstrumentsService()
                    .getAccruedInterestsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (AccruedInterest accruedInterest : accruedInterests) {
                log.info("НКД для figi {}: {}", figi, accruedInterest);
            }
        }
        //инструмент с типом bond
        var bondFigi = bonds.get(0).getFigi();
        try {
            sandboxApi.getInstrumentsService().getCurrencyByFigiSync(bondFigi);
        } catch (ApiRuntimeException e) {
            log.info(e.toString());
        }
        //Получаем информацию о купонах облигации
        var bondCoupons = sandboxApi.getInstrumentsService().getBondCouponsSync(bondFigi, Instant.now().minus(30, ChronoUnit.DAYS), Instant.now());
        for (Coupon bondCoupon : bondCoupons) {
            var couponDate = bondCoupon.getCouponDate();
            var couponType = bondCoupon.getCouponType().getDescriptorForType();
            var payment = moneyValueToBigDecimal(bondCoupon.getPayOneBond());
            log.info("выплаты по купонам. дата: {}, тип: {}, выплата на 1 облигацию: {}", couponDate, couponType, payment);
        }


    }
    private static void currenciesInformation(){
        var currencies = sandboxApi.getInstrumentsService().getTradableCurrenciesSync();
        for (int i = 0; i < Math.min(currencies.size(), 3); i++) {
            var cur = currencies.get(i);
            var uid = cur.getUid();
            var curEx = sandboxApi.getInstrumentsService().getCurrencyByUidSync(uid);
            log.info(curEx.toString());
        }

    }
    private void assetsInformation() {
        //Получаем список активов
        var assets = sandboxApi.getInstrumentsService().getAssetsSync().stream().limit(5).collect(Collectors.toList());
        for (Asset asset : assets) {
            log.info("актив. uid : {}, имя: {}, тип: {}", asset.getUid(), asset.getName(), asset.getType());
        }
        //Получаем подробную информацию о активе
        var uid = assets.get(0).getUid();
        log.info(uid);
        var assetBy = sandboxApi.getInstrumentsService().getAssetBySync(uid);
        log.info("подробная информация об активе. описание: {}, статус: {}, бренд: {}", assetBy.getDescription(), assetBy.getStatus(), assetBy.getBrand().getInfo());
        //System.out.println(shares);
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

    private void shareInformation() {
        var shares = sandboxApi.getInstrumentsService().getTradableSharesSync();
        //Для 3 акций выводим список событий по выплате дивидендов
        for (int i = 0; i < Math.min(shares.size(), 3); i++) {
            var share = shares.get(i);
            var figi = share.getFigi();
            var dividends =
                    sandboxApi.getInstrumentsService().getDividendsSync(figi, Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS));
            for (Dividend dividend : dividends) {
                log.info("дивиденд для figi {}: {}", figi, dividend);
            }
        }
    }

    private void futuresInformation() {
        var futures = sandboxApi.getInstrumentsService().getTradableFuturesSync();
        //Для 3 фьючерсов выводим размер обеспечения
        for (int i = 0; i < Math.min(futures.size(), 3); i++) {
            var future = futures.get(i);
            var figi = future.getFigi();
            var futuresMargin = sandboxApi.getInstrumentsService().getFuturesMarginSync(figi);
            log.info("гарантийное обеспечение при покупке для figi {}: {}", figi,
                    moneyValueToBigDecimal(futuresMargin.getInitialMarginOnBuy()));
            log.info("гарантийное обеспечение при продаже для figi {}: {}", figi,
                    moneyValueToBigDecimal(futuresMargin.getInitialMarginOnSell()));
            log.info("шаг цены figi для {}: {}", figi, quotationToBigDecimal(futuresMargin.getMinPriceIncrement()));
            log.info("стоимость шага цены для figi {}: {}", figi,
                    quotationToBigDecimal(futuresMargin.getMinPriceIncrementAmount()));
        }
    }
}
