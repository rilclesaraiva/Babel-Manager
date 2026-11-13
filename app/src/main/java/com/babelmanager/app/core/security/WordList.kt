package com.babelmanager.app.core.security

/**
 * Lista própria de palavras comuns em inglês (4 a 8 letras) para senhas de palavras.
 * A entropia mostrada ao usuário é calculada a partir do tamanho real desta lista.
 */
object WordList {
    val words: List<String> = RAW.split(' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

private const val RAW = """
able acid acorn actor adapt admit adult agent agree ahead alarm album alert alien alley allow alone alpha
amber ample angle ankle apple april arena argue armor arrow aside asset atlas attic audio avoid awake award
bacon badge baker basic basin batch beach beard beast begin being bench berry bikini blank blast blend bless
blink bloom board boost bored bottle bounce brain brave bread brick bridge brief bring broad brook brush bucket
buddy build bunny burst butter cabin cable cactus camel candle canvas canyon carbon cargo carpet carry castle
casual catch cause cedar cellar chair chalk charm chase cheese cherry chess chief child chill choice chorus
circle city civil claim clay clean clever click cliff climb clock cloth cloud clover clown coach coast cobra
coconut coffee comet copper coral corner cotton couch cousin cover craft crane crash crayon cream creek crisp
crowd crown crumb crystal cube curve cycle daily daisy dance dapple dawn decade decent delta denim depth desert
design detail diary diesel digit dinner diver dizzy doctor dolphin domain donkey dragon drama dream drift drink
drive drum dusty eager eagle early earth easel echo edge elbow elder elite ember empty enjoy enter equal error
essay event exact exit extra fabric face fairy faith falcon family fancy farm fever field fiery figure film final
finch finger fire first fish flame flash fleet flint float flock flood floor flour flute focus  forest forget
fossil frame fresh friend frog front frost fruit fudge funny galaxy garden garlic gazelle gentle ghost giant ginger
giraffe glad glass glide globe glove glow goat gold goose grace grain grape grass gravel green grid grill grove
guard guest guide guitar habit hammer hand happy harbor hawk hazel heart heavy hedge helmet hello hero hidden
hill hobby honey hood horse hotel house human humor hungry hunter icon idea igloo image inch index inner insect
iron island ivory jacket jaguar jelly jewel jigsaw jolly journey joyful judge juice jungle junior kettle 
kiwi kitten knight koala label ladder lake lamp lantern laptop laser latch laugh lava lawn layer leaf lemon
lens leopard lesson level light lilac limit linen lion liquid lively lizard lobster local logic lotus lucky
lunar lunch magic magnet mango maple marble market mason meadow melon mentor metal meter middle might mimic
mineral mirror mist model moment monkey moon moral motor mount mouse movie muffin museum music mystic napkin
narrow nature navy needle nerve nest night noble noise north novel nurse nutmeg oasis ocean olive onion opera
orange orbit orchid organ otter outer oval oxygen paddle pagoda paint palace panda paper parade parrot pastry
patch peace peach pearl pebble pencil penguin pepper piano picnic pilot pine pixel pizza planet plaza pocket
poem polar pond poppy potato powder prairie prism pulse puppy puzzle quartz queen quick quiet quilt rabbit
radar radio rain raven reach ribbon ridge river robin robot rocket rose round royal ruby rural saddle safari
sailor salad salmon sand scarf scene school scout season shadow shell shine shore silent silk silver simple
sketch sleep slope smile smoke snack snow solar solid sound spark spice spider spoon spring square squid
stable star steam stone storm story sugar summer sunny swan sweet swift table tango teapot tender thunder
ticket tiger timber toast tomato topaz tower trail train travel tulip tunnel turtle twist umbrella unique
urban valley vanilla velvet vessel violet vivid voyage walnut water wave whale wheat whisper willow window
winter wisdom wolf wonder woods yacht yellow yogurt zebra zenith zigzag
"""
