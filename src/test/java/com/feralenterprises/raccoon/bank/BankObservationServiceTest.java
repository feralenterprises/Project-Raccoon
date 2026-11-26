package com.feralenterprises.raccoon.bank;

import static org.junit.Assert.assertEquals;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.core.account.AccountScope;
import com.feralenterprises.raccoon.core.bank.BankCaptureSource;
import com.feralenterprises.raccoon.core.bank.BankFrame;
import com.feralenterprises.raccoon.core.bank.BankStatus;
import com.feralenterprises.raccoon.core.bank.BankVisibility;
import com.feralenterprises.raccoon.core.bank.ContainerRead;
import com.feralenterprises.raccoon.core.bank.ItemKind;
import com.feralenterprises.raccoon.core.bank.Ownership;
import com.feralenterprises.raccoon.core.bank.RawSlot;
import com.feralenterprises.raccoon.core.bank.TabWitness;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

/** Adapter wiring: lifecycle tick first, then bank observation; item classification. */
public class BankObservationServiceTest
{
	private String gameState = "LOGGED_IN";
	private long now = 1_000_000_000L;
	private int tick = 500;
	private AccountLifecycleService accounts;
	private BankObservationService bank;

	@Before
	public void setUp()
	{
		accounts = new AccountLifecycleService(() -> gameState, () -> 77L, () -> Collections.singleton("MEMBERS"), () -> now);
		BankCaptureSource source = new BankCaptureSource()
		{
			@Override
			public BankVisibility visibility()
			{
				return BankVisibility.OPEN;
			}

			@Override
			public BankFrame capture(AccountScope scope, long tickIndex)
			{
				return new BankFrame(scope, tickIndex, ContainerRead.present(Arrays.asList(RawSlot.item(995, 3))),
					ContainerRead.missing(), ContainerRead.missing(), TabWitness.of(1, 0, 0, 0, 0, 0, 0, 0, 0));
			}
		};
		bank = new BankObservationService(accounts, source, () -> tick);
		accounts.start();
		bank.reset();
	}

	private void gameTick()
	{
		tick++;
		now += 600_000_000L;
		accounts.onGameTick();
		bank.onGameTick();
	}

	@Test
	public void qualifiesThroughTheAdapterAfterTwoTicks()
	{
		gameTick(); // lifecycle activates first, then the first bank observation in the same tick
		assertEquals(BankStatus.WAITING, bank.view().snapshot().status());
		gameTick();
		assertEquals(BankStatus.QUALIFIED_CURRENT, bank.view().snapshot().status());
		assertEquals(3, bank.view().bankOwnership(995).quantity());
	}

	@Test
	public void lifecycleStopRevokesBankAuthority()
	{
		gameTick();
		gameTick();
		gameTick();
		accounts.stop();
		assertEquals(Ownership.Kind.UNKNOWN, bank.view().bankOwnership(995).kind());
	}

	@Test
	public void itemClassification()
	{
		assertEquals(ItemKind.ITEM, ItemClassifier.classify(995, -1, -1));
		assertEquals(ItemKind.PLACEHOLDER, ItemClassifier.classify(995, ItemClassifier.PLACEHOLDER_TEMPLATE, -1));
		assertEquals(ItemKind.FILLER, ItemClassifier.classify(net.runelite.api.gameval.ItemID.BANK_FILLER, -1, -1));
		assertEquals(ItemKind.NOTED, ItemClassifier.classify(996, -1, ItemClassifier.NOTED));
		assertEquals(ItemKind.UNREADABLE, ItemClassifier.classify(995, 12345, -1));
		assertEquals(ItemKind.UNREADABLE, ItemClassifier.classify(995, -1, 4));
	}
}
