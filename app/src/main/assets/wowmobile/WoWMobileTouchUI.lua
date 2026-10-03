local frame = CreateFrame("Frame")
frame:RegisterEvent("PLAYER_ENTERING_WORLD")
frame:SetScript("OnEvent", function()
    if MainMenuBar then
        MainMenuBar:SetScale(1.35)
    end
end)
