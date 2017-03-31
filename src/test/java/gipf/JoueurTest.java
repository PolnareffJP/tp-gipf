package gipf;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.github.npathai.hamcrestopt.OptionalMatchers.*;

public class JoueurTest {

	public static List<String> usernames = Arrays.asList("baroqueen", "cobrag", "vikingkong", "preaster",
			"fickleSkeleton", "SnowTea", "AfternoonTerror", "JokeCherry", "JealousPelican", "PositiveLamb");

	private Connection con;
	private List<Joueur> joueurs;

	@Before
	public void setUp() throws SQLException {
		con = Main.connect();
		Main.clean(con);
		joueurs = inscrire(con);
	}

	@After
	public void close() throws SQLException {
		con.close();
	}

	public static List<Joueur> inscrire(Connection con) {
		return usernames.stream().map(username -> {
			try {
				return Joueur.inscrire(username, UUID.randomUUID().toString(), username + "@univ-valenciennes.fr", con);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}).collect(Collectors.toList());
	}

	@Test
	public void testEquals() {
		Joueur j1 = new Joueur("baroqueen", "", "", 0);
		assertEquals(j1, joueurs.get(0));
	}

	@Test
	public void testInscrire() {
		for (int i = 0; i < usernames.size(); i++) {
			assertEquals(usernames.get(i), joueurs.get(i).getLogin());
			assertEquals(1000, joueurs.get(i).getElo(), 0);
			assertEquals(usernames.get(i) + "@univ-valenciennes.fr", joueurs.get(i).getEmail());
		}
	}

	@Test(expected = InscriptionException.class)
	public void testInscrireDoublon() throws SQLException, InscriptionException {
		String un = usernames.get(0);
		Joueur.inscrire(un, UUID.randomUUID().toString(), "toto@univ-valenciennes.fr", con);
	}

	@Test(expected = InscriptionException.class)
	public void testInscrireBadMail() throws SQLException, InscriptionException {
		Joueur.inscrire("toto", UUID.randomUUID().toString(), "univ-valenciennes.fr", con);
	}

	@Test(expected = InscriptionException.class)
	public void testInscrireDoubleMail() throws SQLException, InscriptionException {
		Joueur.inscrire("toto", UUID.randomUUID().toString(), usernames.get(0) + "@univ-valenciennes.fr", con);
	}

	@Test
	public void testLoad() throws SQLException {
		for (String u : usernames) {
			Optional<Joueur> gj = Joueur.load(u, con);
			assertThat(gj, isPresent());
			Joueur j = gj.get();
			assertEquals(u, j.getLogin());
			assertEquals(1000, j.getElo(), 0);
			assertEquals(u + "@univ-valenciennes.fr", j.getEmail());
		}
		assertThat(Joueur.load("toto", con), isEmpty());
	}

	@Test
	public void testElo() throws SQLException {
		Joueur j = joueurs.get(0);
		j.addElo(1000);
		assertEquals(2000, j.getElo(), 0);
		j.addElo(-20);
		assertEquals(1980, j.getElo(), 0);
	}

	@Test
	public void testSave() throws SQLException {
		Joueur j = joueurs.get(0);
		j.setPassword("newPass");
		j.addElo(1000);
		j.setEmail("a@b.com");
		j.save(con);

		Joueur loaded = Joueur.load(usernames.get(0), con).get();
		assertTrue(loaded.checkPassword("newPass"));
		assertEquals(2000, loaded.getElo(), 0);
		assertEquals("a@b.com", loaded.getEmail());
	}

	@Test
	public void testClassementElo() throws SQLException {
		Random rand = new Random(0);
		List<Partie> parties = PartieTest.randParties(joueurs, rand, con);
		PartieTest.randGagnants(parties, rand, con);

		Map<String, Double> refElo = joueurs.stream().collect(Collectors.toMap(Joueur::getLogin, Joueur::getElo));

		List<Joueur> classement = Joueur.loadByElo(con);
		assertThat(classement, containsInAnyOrder(joueurs.toArray()));

		for (Joueur j : classement) {
			assertEquals(refElo.get(j.getLogin()), j.getElo(), 0.5);
		}

		for (int i = 0; i < classement.size() - 1; i++) {
			assertThat(classement.get(i).getElo(), greaterThanOrEqualTo(classement.get(i + 1).getElo()));
		}
	}

}
